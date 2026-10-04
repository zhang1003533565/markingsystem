import { useRef } from 'react';
import { NodeViewWrapper } from '@tiptap/react';

function AnnouncementResizableImage({ node, updateAttributes, selected }) {
  const wrapperRef = useRef(null);
  const startStateRef = useRef(null);
  const width = Number(node.attrs.width) || null;

  const handleResizeStart = (event) => {
    event.preventDefault();
    event.stopPropagation();

    const wrapperWidth = wrapperRef.current?.parentElement?.clientWidth || 0;
    startStateRef.current = {
      startX: event.clientX,
      startWidth: width || wrapperRef.current?.querySelector('img')?.clientWidth || 320,
      wrapperWidth
    };

    const handleMouseMove = (moveEvent) => {
      if (!startStateRef.current) {
        return;
      }

      const deltaX = moveEvent.clientX - startStateRef.current.startX;
      const rawWidth = startStateRef.current.startWidth + deltaX;
      const maxWidth = startStateRef.current.wrapperWidth || rawWidth;
      const nextWidth = Math.max(120, Math.min(rawWidth, maxWidth));
      updateAttributes({ width: Math.round(nextWidth) });
    };

    const handleMouseUp = () => {
      startStateRef.current = null;
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  };

  return (
    <NodeViewWrapper
      as="span"
      className={`announcement-resizable-image ${selected ? 'is-selected' : ''}`}
      data-align={node.attrs.align || 'left'}
      ref={wrapperRef}
    >
      <img
        src={node.attrs.src}
        alt={node.attrs.alt || ''}
        className="announcement-editor-image-node"
        style={width ? { width: `${width}px` } : undefined}
      />
      <button
        type="button"
        className="announcement-image-resize-handle"
        onMouseDown={handleResizeStart}
        aria-label="调整图片大小"
      />
    </NodeViewWrapper>
  );
}

export default AnnouncementResizableImage;
