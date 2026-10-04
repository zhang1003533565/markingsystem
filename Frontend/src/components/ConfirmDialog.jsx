import { useCallback, useEffect, useRef, useState } from 'react';
import { LockOutlined, SafetyCertificateOutlined } from './AcademicIcons';
import './ConfirmDialog.css';
import { registerConfirmDialogHost } from './confirmDialogService';

function ConfirmDialogHost() {
  const [dialog, setDialog] = useState(null);
  const resolverRef = useRef(null);
  const cancelButtonRef = useRef(null);
  const confirmButtonRef = useRef(null);

  const closeDialog = useCallback((confirmed) => {
    const resolve = resolverRef.current;
    resolverRef.current = null;
    setDialog(null);
    resolve?.(confirmed);
  }, []);

  useEffect(() => {
    const openDialog = (options = {}, resolve) => {
      resolverRef.current?.(false);
      resolverRef.current = resolve;
      setDialog({
        title: '请确认操作',
        message: '',
        confirmText: '确认',
        cancelText: '取消',
        showCancel: true,
        tone: 'warning',
        ...options
      });
    };

    const unregister = registerConfirmDialogHost(openDialog);

    return () => {
      unregister();
      resolverRef.current?.(false);
      resolverRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!dialog) return undefined;
    const previousOverflow = document.body.style.overflow;
    const previousFocus = document.activeElement;
    document.body.style.overflow = 'hidden';
    window.requestAnimationFrame(() => {
      (dialog.showCancel ? cancelButtonRef.current : confirmButtonRef.current)?.focus();
    });

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeDialog(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
      previousFocus?.focus?.();
    };
  }, [closeDialog, dialog]);

  if (!dialog) return null;

  const tone = ['danger', 'warning', 'primary'].includes(dialog.tone) ? dialog.tone : 'warning';
  const isPermissionDialog = dialog.variant === 'permission';

  return (
    <div
      className="confirm-dialog-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) closeDialog(false);
      }}
    >
      <section
        className={`confirm-dialog confirm-dialog-${tone}${isPermissionDialog ? ' confirm-dialog-permission' : ''}`}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-message"
      >
        <div className="confirm-dialog-icon" aria-hidden="true">
          {isPermissionDialog ? (
            <SafetyCertificateOutlined />
          ) : tone === 'danger' ? '!' : tone === 'primary' ? '✓' : '?'}
        </div>
        <div className="confirm-dialog-content">
          <h2 id="confirm-dialog-title">{dialog.title}</h2>
          <div id="confirm-dialog-message" className="confirm-dialog-message">
            {dialog.message}
          </div>
          {dialog.detail ? <div className="confirm-dialog-detail">{dialog.detail}</div> : null}
        </div>
        {isPermissionDialog && dialog.permission ? (
          <div className="confirm-dialog-permission-card">
            <div className="confirm-dialog-permission-row">
              <span className="confirm-dialog-creator-avatar" aria-hidden="true">
                {String(dialog.permission.creatorName || '?').slice(0, 1)}
              </span>
              <span className="confirm-dialog-permission-label">创建人</span>
              <strong>{dialog.permission.creatorName || '未记录'}</strong>
            </div>
            <div className="confirm-dialog-permission-row rule">
              <span className="confirm-dialog-rule-icon" aria-hidden="true">
                <LockOutlined />
              </span>
              <span className="confirm-dialog-permission-label">权限规则</span>
              <strong>{dialog.permission.rule}</strong>
            </div>
          </div>
        ) : null}
        <div className="confirm-dialog-actions">
          {dialog.showCancel ? (
            <button
              ref={cancelButtonRef}
              type="button"
              className="confirm-dialog-button cancel"
              onClick={() => closeDialog(false)}
            >
              {dialog.cancelText}
            </button>
          ) : null}
          {dialog.alternativeText ? (
            <button
              type="button"
              className="confirm-dialog-button alternative"
              onClick={() => closeDialog('alternative')}
            >
              {dialog.alternativeText}
            </button>
          ) : null}
          <button
            ref={confirmButtonRef}
            type="button"
            className={`confirm-dialog-button confirm ${tone}`}
            onClick={() => closeDialog(true)}
          >
            {dialog.confirmText}
          </button>
        </div>
      </section>
    </div>
  );
}

export default ConfirmDialogHost;
