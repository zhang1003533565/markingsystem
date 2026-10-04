const normalizeIdentity = (value) => String(value ?? '').trim();

export const canEditExtraScoreTemplate = (template, user) => {
  const role = String(user?.role || '').trim().toUpperCase();
  if (role === 'ADMIN') {
    return true;
  }

  return (
    role === 'TEACHER' &&
    normalizeIdentity(user?.id) !== '' &&
    normalizeIdentity(user?.id) === normalizeIdentity(template?.creatorId)
  );
};

export const getExtraScoreTemplateEditDeniedMessage = (template) => {
  const creatorName = String(template?.creatorName || '').trim();
  return creatorName
    ? `该模板由“${creatorName}”创建，请联系创建人进行修改`
    : '该模板由其他用户创建，请联系创建人进行修改';
};
