let openConfirmDialog = null;

export const confirmAction = (options) => new Promise((resolve) => {
  if (!openConfirmDialog) {
    console.warn('确认弹窗尚未挂载，本次操作已取消');
    resolve(false);
    return;
  }
  openConfirmDialog(typeof options === 'string' ? { message: options } : options, resolve);
});

export const noticeAction = (options) => confirmAction({
  ...(typeof options === 'string' ? { message: options } : options),
  showCancel: false,
  confirmText: typeof options === 'string' ? '我知道了' : options?.confirmText || '我知道了',
});

export const registerConfirmDialogHost = (openDialog) => {
  openConfirmDialog = openDialog;

  return () => {
    if (openConfirmDialog === openDialog) {
      openConfirmDialog = null;
    }
  };
};
