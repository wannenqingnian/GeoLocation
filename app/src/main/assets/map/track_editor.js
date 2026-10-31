(function () {
    const namespace = 'http://www.w3.org/2000/svg';
    let adapter = null;
    let configuration = null;
    let drag = null;
    let suppressedUntil = 0;
    let renderFrame = null;
    let projectedPath = null;
    let nativeTrackRendered = false;
    const root = document.createElementNS(namespace, 'svg');
    root.id = 'trace-track-editor';
    root.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;z-index:2147483000;pointer-events:none;overflow:hidden;touch-action:none;';
    const pathShape = document.createElementNS(namespace, 'path');
    pathShape.setAttribute('fill', 'none');
    pathShape.setAttribute('stroke', '#1769d8');
    pathShape.setAttribute('stroke-width', '5');
    pathShape.setAttribute('stroke-linejoin', 'round');
    const lapPreviewGroup = document.createElementNS(namespace, 'g');
    lapPreviewGroup.id = 'trace-track-lap-previews';
    lapPreviewGroup.style.pointerEvents = 'none';
    const startHit = document.createElementNS(namespace, 'circle');
    startHit.setAttribute('r', '32');
    startHit.setAttribute('fill', 'transparent');
    startHit.setAttribute('pointer-events', 'all');
    startHit.style.touchAction = 'none';
    const startMarker = document.createElementNS(namespace, 'g');
    const startCircle = document.createElementNS(namespace, 'circle');
    startCircle.setAttribute('r', '14');
    startCircle.setAttribute('fill', '#ffffff');
    startCircle.setAttribute('stroke', '#1769d8');
    startCircle.setAttribute('stroke-width', '2');
    const startLabel = document.createElementNS(namespace, 'text');
    startLabel.textContent = '起';
    startLabel.setAttribute('text-anchor', 'middle');
    startLabel.setAttribute('y', '5');
    startLabel.setAttribute('fill', '#1769d8');
    startLabel.setAttribute('font-size', '13');
    startMarker.style.pointerEvents = 'none';
    startMarker.append(startCircle, startLabel);
    const moveHit = document.createElementNS(namespace, 'circle');
    moveHit.id = 'trace-track-move-handle-hit';
    moveHit.setAttribute('r', '34');
    moveHit.setAttribute('fill', 'transparent');
    moveHit.setAttribute('pointer-events', 'all');
    moveHit.style.touchAction = 'none';
    const moveMarker = document.createElementNS(namespace, 'g');
    moveMarker.id = 'trace-track-move-handle';
    moveMarker.style.pointerEvents = 'none';
    const moveIconPath = 'M0 -4V-11M0 -11L-4 -7M0 -11L4 -7M0 4V11M0 11L-4 7M0 11L4 7M-4 0H-11M-11 0L-7 -4M-11 0L-7 4M4 0H11M11 0L7 -4M11 0L7 4';
    const moveIcon = document.createElementNS(namespace, 'path');
    moveIcon.setAttribute('d', moveIconPath);
    moveIcon.setAttribute('fill', 'none');
    moveIcon.setAttribute('stroke', '#1769d8');
    moveIcon.setAttribute('stroke-width', '2');
    moveIcon.setAttribute('stroke-linecap', 'square');
    moveIcon.setAttribute('stroke-linejoin', 'miter');
    const moveCenter = document.createElementNS(namespace, 'circle');
    moveCenter.setAttribute('r', '3.5');
    moveCenter.setAttribute('fill', '#1769d8');
    moveCenter.setAttribute('stroke', 'none');
    moveMarker.append(moveIcon, moveCenter);
    root.append(pathShape, lapPreviewGroup, moveMarker, startMarker, moveHit, startHit);

    function emit(type, values) {
        if (window.AndroidMap && window.AndroidMap.onTrackEditorEvent) {
            window.AndroidMap.onTrackEditorEvent(JSON.stringify(Object.assign({type}, values)));
        }
    }
    function project(point) { return adapter.project(Number(point.latitude), Number(point.longitude)); }
    function pointFromEvent(event) {
        const bounds = root.getBoundingClientRect();
        return {x: event.clientX - bounds.left, y: event.clientY - bounds.top};
    }
    function nearestPoint(target, points = projectedPath || configuration.path.map(project)) {
        let nearest = null;
        let best = Infinity;
        for (let index = 1; index < points.length; index++) {
            const from = points[index - 1], to = points[index];
            const deltaX = to.x - from.x, deltaY = to.y - from.y;
            const denominator = deltaX * deltaX + deltaY * deltaY;
            const fraction = denominator ? Math.max(0, Math.min(1, ((target.x - from.x) * deltaX + (target.y - from.y) * deltaY) / denominator)) : 0;
            const candidate = {x: from.x + deltaX * fraction, y: from.y + deltaY * fraction};
            const distance = Math.hypot(candidate.x - target.x, candidate.y - target.y);
            if (distance < best) { best = distance; nearest = candidate; }
        }
        return nearest;
    }
    function trackCenter(points = projectedPath || configuration.path.map(project)) {
        if (!points.length) return null;
        let minimumX = points[0].x, maximumX = points[0].x;
        let minimumY = points[0].y, maximumY = points[0].y;
        points.forEach(point => {
            minimumX = Math.min(minimumX, point.x); maximumX = Math.max(maximumX, point.x);
            minimumY = Math.min(minimumY, point.y); maximumY = Math.max(maximumY, point.y);
        });
        return {x:(minimumX + maximumX) / 2, y:(minimumY + maximumY) / 2};
    }
    function setAttributeIfChanged(node, name, value) {
        const text = String(value);
        if (node.getAttribute(name) !== text) node.setAttribute(name, text);
    }
    function setStyleIfChanged(node, name, value) {
        if (node.style[name] !== value) node.style[name] = value;
    }
    function clearTransform(node) {
        if (node.hasAttribute('transform')) node.removeAttribute('transform');
    }
    function renderLapPreviews(visible) {
        const previews = visible && Array.isArray(configuration.lapPreviews) ? configuration.lapPreviews : [];
        const colors = ['#ff5d57', '#26a269', '#f59e0b', '#8b5cf6', '#0891b2'];
        while (lapPreviewGroup.children.length > previews.length) lapPreviewGroup.lastChild.remove();
        while (lapPreviewGroup.children.length < previews.length) {
            const shape = document.createElementNS(namespace, 'path');
            setAttributeIfChanged(shape, 'fill', 'none');
            setAttributeIfChanged(shape, 'stroke-width', '2.25');
            setAttributeIfChanged(shape, 'stroke-linecap', 'round');
            setAttributeIfChanged(shape, 'stroke-linejoin', 'round');
            setAttributeIfChanged(shape, 'opacity', '.88');
            lapPreviewGroup.appendChild(shape);
        }
        previews.forEach((preview, index) => {
            const data = preview.map((point, pointIndex) => { const pixel = project(point); return (pointIndex ? 'L' : 'M') + pixel.x + ' ' + pixel.y; }).join(' ');
            const shape = lapPreviewGroup.children[index];
            setAttributeIfChanged(shape, 'stroke', colors[index % colors.length]);
            setAttributeIfChanged(shape, 'd', data);
        });
        setStyleIfChanged(lapPreviewGroup, 'display', previews.length ? '' : 'none');
    }
    function render() {
        if (!adapter || !configuration || drag || (adapter.ready && !adapter.ready())) return;
        setStyleIfChanged(root, 'display', configuration.stage ? 'block' : 'none');
        const editing = configuration.stage === 'ADJUST';
        const displaying = configuration.stage === 'DISPLAY';
        const showPath = editing || displaying;
        nativeTrackRendered = adapter.renderTrack?.(showPath ? configuration : null) === true;
        setStyleIfChanged(root, 'pointerEvents', 'none');
        setStyleIfChanged(pathShape, 'display', showPath && !nativeTrackRendered ? '' : 'none');
        setStyleIfChanged(moveMarker, 'display', editing ? '' : 'none');
        setStyleIfChanged(moveHit, 'display', editing ? '' : 'none');
        setStyleIfChanged(pathShape, 'pointerEvents', 'none');
        setStyleIfChanged(moveHit, 'pointerEvents', editing ? 'all' : 'none');
        setStyleIfChanged(startMarker, 'display', editing && configuration.start ? '' : 'none');
        setStyleIfChanged(startHit, 'display', editing && configuration.start ? '' : 'none');
        setStyleIfChanged(startHit, 'pointerEvents', editing ? 'all' : 'none');
        renderLapPreviews(showPath && !nativeTrackRendered);
        if (nativeTrackRendered && !editing) return;
        if (showPath && configuration.path?.length) {
            clearTransform(pathShape); clearTransform(lapPreviewGroup); clearTransform(moveHit); clearTransform(startHit);
            projectedPath = configuration.path.map(project);
            const data = projectedPath.map((pixel, index) => (index ? 'L' : 'M') + pixel.x + ' ' + pixel.y).join(' ');
            setAttributeIfChanged(pathShape, 'd', data);
            const center = trackCenter();
            if (center) {
                setAttributeIfChanged(moveMarker, 'transform', 'translate(' + center.x + ' ' + center.y + ')');
                setAttributeIfChanged(moveHit, 'cx', center.x); setAttributeIfChanged(moveHit, 'cy', center.y);
            }
            if (configuration.start) {
                const pixel = project(configuration.start);
                setAttributeIfChanged(startMarker, 'transform', 'translate(' + pixel.x + ' ' + pixel.y + ')');
                setAttributeIfChanged(startHit, 'cx', pixel.x); setAttributeIfChanged(startHit, 'cy', pixel.y);
            }
        }
    }
    function scheduleRender() {
        if (renderFrame !== null || !configuration?.stage) return;
        renderFrame = requestAnimationFrame(() => {
            renderFrame = null;
            render();
        });
    }
    function fitVisibleTrack(previous) {
        if (!adapter?.fit || configuration?.stage !== 'ADJUST' || configuration.path?.length < 4) return;
        // Android's reserved inset includes the 15px handle and 20px marker gap.
        // Center the fit region on the drawer surface, allowing its lower half behind glass.
        const drawerHeight = (Number(configuration.viewportBottom) || Math.round(window.innerHeight * .65) + 35) - 35;
        const bottom = Math.max(24, 2 * drawerHeight - window.innerHeight + 112);
        const points = configuration.path.concat(...(configuration.lapPreviews || []));
        if (configuration.start) points.push(configuration.start);
        const entering = previous?.stage !== 'ADJUST';
        const viewportChanged = previous?.viewportBottom !== configuration.viewportBottom;
        const shapeChanged = previous?.scale !== configuration.scale || previous?.rotationDegrees !== configuration.rotationDegrees ||
            previous?.lapPreviews?.length !== configuration.lapPreviews?.length;
        // Explicit map/handle moves remain under user control. Only size/angle changes
        // that outgrow the fit region need another camera fit.
        if (!entering && !viewportChanged && !shapeChanged) return;
        const outside = entering || viewportChanged || points.some(point => {
            const pixel = project(point);
            return pixel.x < 23.5 || pixel.x > window.innerWidth - 23.5 ||
                pixel.y < 111.5 || pixel.y > window.innerHeight - bottom + .5;
        });
        if (outside) adapter.fit(points, bottom);
    }
    function startPointer(event, kind) {
        if (configuration?.stage !== 'ADJUST' || drag || event.isPrimary === false) return;
        // Flush the current projection before locking the map for pointer interaction.
        render();
        event.stopPropagation(); event.preventDefault();
        const point = pointFromEvent(event);
        root.setPointerCapture(event.pointerId);
        adapter.setGestures(false);
        drag = {
            kind, pointerId:event.pointerId, initial:point, latest:point, moved:false, gesturesLocked:true,
            points: projectedPath || configuration.path.map(project),
            center: trackCenter(),
            start: configuration.start ? project(configuration.start) : null,
        };
        suppressedUntil = Date.now() + 700;
        if (kind === 'track') {
            moveIcon.setAttribute('stroke','#1769d8');
            moveCenter.setAttribute('fill','#1769d8');
        } else startCircle.setAttribute('r','17');
        emit('dragStart', {kind});
    }
    moveHit.addEventListener('pointerdown',event=>startPointer(event,'track'));
    startHit.addEventListener('pointerdown',event=>startPointer(event,'start'));
    root.addEventListener('pointermove',event=>{
        if (!drag || drag.pointerId !== event.pointerId) return;
        event.stopPropagation(); event.preventDefault();
        const point = pointFromEvent(event);
        drag.latest = point;
        const deltaX = point.x - drag.initial.x, deltaY = point.y - drag.initial.y;
        if (Math.hypot(deltaX,deltaY) < 2 && !drag.moved) return;
        drag.moved = true;
        if (drag.kind === 'track') {
            if (nativeTrackRendered) {
                const from = adapter.unproject(drag.initial.x, drag.initial.y);
                const to = adapter.unproject(point.x, point.y);
                const shift = value => Object.assign({}, value, {
                    latitude: Number(value.latitude) + to.latitude - from.latitude,
                    longitude: Number(value.longitude) + to.longitude - from.longitude,
                });
                adapter.renderTrack(Object.assign({}, configuration, {
                    path: configuration.path.map(shift),
                    lapPreviews: (configuration.lapPreviews || []).map(lap => lap.map(shift)),
                }));
            }
            const transform='translate('+deltaX+' '+deltaY+')';
            pathShape.setAttribute('transform',transform); lapPreviewGroup.setAttribute('transform',transform); moveHit.setAttribute('transform',transform); startHit.setAttribute('transform',transform);
            const center=drag.center;
            if(center)moveMarker.setAttribute('transform','translate('+(center.x+deltaX)+' '+(center.y+deltaY)+')');
            const start=drag.start;
            if(start)startMarker.setAttribute('transform','translate('+(start.x+deltaX)+' '+(start.y+deltaY)+')');
        } else {
            const nearest=nearestPoint(point,drag.points);
            if(nearest){startMarker.setAttribute('transform','translate('+nearest.x+' '+nearest.y+')');startHit.setAttribute('cx',nearest.x);startHit.setAttribute('cy',nearest.y);}
        }
    });
    function endPointer(event, cancelled) {
        if (!drag || drag.pointerId !== event.pointerId) return;
        const finished=drag;
        drag=null;
        suppressedUntil=Date.now()+700;
        if(finished.gesturesLocked)adapter.setGestures(true);
        moveIcon.setAttribute('stroke','#1769d8'); moveCenter.setAttribute('fill','#1769d8'); startCircle.setAttribute('r','14');
        if(finished.moved && !cancelled){
            if(finished.kind==='track'){const from=adapter.unproject(finished.initial.x,finished.initial.y),to=adapter.unproject(finished.latest.x,finished.latest.y);emit('move',{from,to});}
            else {const target=nearestPoint(finished.latest,finished.points);if(target)emit('start',adapter.unproject(target.x,target.y));}
        }
        if(cancelled || !finished.moved)render();
    }
    root.addEventListener('pointerup',event=>endPointer(event,false));
    root.addEventListener('pointercancel',event=>endPointer(event,true));
    window.addEventListener('pointerdown',event=>{if(event.isPrimary===false && drag)endPointer({pointerId:drag.pointerId},true);},true);
    root.addEventListener('lostpointercapture',event=>endPointer(event,true));
    root.addEventListener('click',event=>{event.stopPropagation();event.preventDefault();});
    window.TraceTrackEditor = {
        attach(value) {
            adapter=value;
            document.body.appendChild(root);
            projectedPath=null;
            adapter.listen(scheduleRender);
            window.addEventListener('resize',scheduleRender);
            render();
        },
        set(value) {
            const previous=configuration;
            configuration=value;
            projectedPath=null;
            if(renderFrame!==null){cancelAnimationFrame(renderFrame);renderFrame=null;}
            if(value.stage !== 'ADJUST'){
                if(drag?.gesturesLocked)adapter?.setGestures(true);
                drag=null;
                suppressedUntil=0;
            }
            if(!value.stage){
                root.style.display='none';
                moveHit.style.pointerEvents=startHit.style.pointerEvents='none';
                renderLapPreviews(false);
            }
            fitVisibleTrack(previous);
            render();
        },
        active(){return configuration?.stage === 'ADJUST';},
        suppressClick(){return configuration?.stage === 'ADJUST'||Date.now()<suppressedUntil;},
        refresh(){scheduleRender();},
    };
})();
