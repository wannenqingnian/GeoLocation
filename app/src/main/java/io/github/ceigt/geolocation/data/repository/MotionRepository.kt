package io.github.ceigt.geolocation.data.repository

import android.content.Context
import io.github.ceigt.geolocation.data.model.motion.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 本地运动采集样本库持久化与文件导出仓库
 */
class MotionRepository(private val context: Context) {

    private val samplesDir: File by lazy {
        File(context.filesDir, "motion_samples").apply { if (!exists()) mkdirs() }
    }

    /**
     * 保存单次完整采集会话至本地独立 JSON 文件
     */
    suspend fun saveSession(session: MotionRecordSession): File = withContext(Dispatchers.IO) {
        val file = File(samplesDir, "${session.id}.json")
        val json = serializeSession(session)
        file.writeText(json, Charsets.UTF_8)
        file
    }

    /**
     * 读取所有已录制的样本摘要列表（不一次性加载几万个样本点，保证列表秒开）
     */
    suspend fun listSessions(): List<MotionRecordSession> = withContext(Dispatchers.IO) {
        val files = samplesDir.listFiles { f -> f.extension == "json" } ?: return@withContext emptyList()
        val list = mutableListOf<MotionRecordSession>()
        for (f in files) {
            runCatching {
                val text = f.readText(Charsets.UTF_8)
                val session = deserializeSession(text, loadSamples = false)
                list.add(session)
            }
        }
        list.sortedByDescending { it.startTimeEpochMs }
    }

    /**
     * 加载完整会话数据（含全部传感器数据点）供离线回放与报告评估
     */
    suspend fun loadFullSession(sessionId: String): MotionRecordSession? = withContext(Dispatchers.IO) {
        val file = File(samplesDir, "$sessionId.json")
        if (!file.exists()) return@withContext null
        runCatching {
            val text = file.readText(Charsets.UTF_8)
            deserializeSession(text, loadSamples = true)
        }.getOrNull()
    }

    /**
     * 删除指定会话文件
     */
    suspend fun deleteSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(samplesDir, "$sessionId.json")
        if (file.exists()) file.delete() else false
    }

    private fun serializeSession(s: MotionRecordSession): String {
        val root = JSONObject()
            .put("id", s.id)
            .put("title", s.title)
            .put("deviceModel", s.deviceModel)
            .put("androidVersion", s.androidVersion)
            .put("carryPosition", s.carryPosition.name)
            .put("activityType", s.activityType.name)
            .put("startTimeEpochMs", s.startTimeEpochMs)
            .put("endTimeEpochMs", s.endTimeEpochMs)
            .put("manualStepCount", s.manualStepCount)
            .put("note", s.note)

        // Availability
        val availObj = JSONObject()
            .put("hasAccelerometer", s.sensorAvailability.hasAccelerometer)
            .put("hasGyroscope", s.sensorAvailability.hasGyroscope)
            .put("hasStepCounter", s.sensorAvailability.hasStepCounter)
            .put("hasStepDetector", s.sensorAvailability.hasStepDetector)
            .put("missingSensors", JSONArray(s.sensorAvailability.missingSensors))
        root.put("sensorAvailability", availObj)

        // Samples
        val sampleArr = JSONArray()
        for (sm in s.samples) {
            val obj = JSONObject()
                .put("i", sm.sampleIndex)
                .put("t", sm.sensorType)
                .put("ts", sm.timestampNanos)
                .put("a", sm.accuracy)

            val valArr = JSONArray()
            for (v in sm.values) {
                valArr.put(v.toDouble())
            }
            obj.put("v", valArr)
            sampleArr.put(obj)
        }
        root.put("samples", sampleArr)

        return root.toString()
    }

    private fun deserializeSession(jsonStr: String, loadSamples: Boolean): MotionRecordSession {
        val root = JSONObject(jsonStr)
        val id = root.getString("id")
        val title = root.optString("title", "未命名采集")
        val deviceModel = root.optString("deviceModel", "Unknown")
        val androidVersion = root.optString("androidVersion", "Unknown")
        val carryPosition = PhoneCarryPosition.fromName(root.optString("carryPosition"))
        val activityType = MotionActivityType.fromName(root.optString("activityType"))
        val startTime = root.optLong("startTimeEpochMs", 0L)
        val endTime = root.optLong("endTimeEpochMs", 0L)
        val manualSteps = root.optInt("manualStepCount", 0)
        val note = root.optString("note", "")

        val availObj = root.optJSONObject("sensorAvailability")
        val missingList = mutableListOf<String>()
        val missingArr = availObj?.optJSONArray("missingSensors")
        if (missingArr != null) {
            for (i in 0 until missingArr.length()) {
                missingList.add(missingArr.getString(i))
            }
        }
        val availability = SensorAvailability(
            hasAccelerometer = availObj?.optBoolean("hasAccelerometer", true) ?: true,
            hasGyroscope = availObj?.optBoolean("hasGyroscope", true) ?: true,
            hasStepCounter = availObj?.optBoolean("hasStepCounter", true) ?: true,
            hasStepDetector = availObj?.optBoolean("hasStepDetector", true) ?: true,
            missingSensors = missingList
        )

        val samplesList = mutableListOf<SensorSample>()
        if (loadSamples) {
            val arr = root.optJSONArray("samples")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val vArr = obj.getJSONArray("v")
                    val values = FloatArray(vArr.length())
                    for (j in 0 until vArr.length()) {
                        values[j] = vArr.getDouble(j).toFloat()
                    }
                    samplesList.add(
                        SensorSample(
                            sampleIndex = obj.optLong("i", i.toLong()),
                            sensorType = obj.getInt("t"),
                            timestampNanos = obj.getLong("ts"),
                            values = values,
                            accuracy = obj.optInt("a", 3)
                        )
                    )
                }
            }
        }

        return MotionRecordSession(
            id = id,
            title = title,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            carryPosition = carryPosition,
            activityType = activityType,
            startTimeEpochMs = startTime,
            endTimeEpochMs = endTime,
            manualStepCount = manualSteps,
            sensorAvailability = availability,
            samples = samplesList,
            note = note
        )
    }
}
