import { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Button, Result, Spin, message } from 'antd';
import { getCurrentAcademicYear } from '../api/admin/academicYear';
import { getStudentAcademicYears } from '../api/student/portal';
import { getStoredUser } from '../utils/authStorage';
import './AcademicYearGuard.css';

const ACADEMIC_YEAR_PATH = '/admin/academic-years';

function getUserRole() {
  const user = getStoredUser();
  return user.role || '';
}

function findCurrentAcademicYear(response) {
  if (response?.current) {
    return response;
  }

  const list = Array.isArray(response?.data)
    ? response.data
    : Array.isArray(response)
      ? response
      : [];

  return list.find((item) => item?.current) || null;
}

function MissingAcademicYearNotice({ role }) {
  const isTeacher = role === 'TEACHER';

  return (
    <div className="academic-year-guard-notice">
      <Result
        status="warning"
        title="当前学年未设置"
        subTitle={isTeacher ? '请联系系统管理员设置当前学年后再使用管理功能。' : '请联系管理员设置当前学年后再使用系统功能。'}
        extra={(
          <Button type="primary" onClick={() => window.location.reload()}>
            重新检查
          </Button>
        )}
      />
    </div>
  );
}

function AcademicYearGuard({ children }) {
  const location = useLocation();
  const [checking, setChecking] = useState(true);
  const [hasCurrentAcademicYear, setHasCurrentAcademicYear] = useState(false);
  const role = getUserRole();

  useEffect(() => {
    let ignore = false;

    const checkCurrentAcademicYear = async () => {
      setChecking(true);
      try {
        const response = role === 'STUDENT' || role === 'CLASS_COMMITTEE'
          ? await getStudentAcademicYears({ silentError: true })
          : await getCurrentAcademicYear({ silentError: true });

        if (!ignore) {
          setHasCurrentAcademicYear(Boolean(findCurrentAcademicYear(response)));
        }
      } catch (error) {
        if (!ignore) {
          setHasCurrentAcademicYear(false);
        }
      } finally {
        if (!ignore) {
          setChecking(false);
        }
      }
    };

    checkCurrentAcademicYear();

    return () => {
      ignore = true;
    };
  }, [role, location.pathname]);

  useEffect(() => {
    if (!checking && !hasCurrentAcademicYear && role === 'ADMIN') {
      message.warning('当前学年未设置，请先设置当前学年');
    }
  }, [checking, hasCurrentAcademicYear, role]);

  if (checking) {
    return (
      <div className="academic-year-guard-loading">
        <Spin size="large" />
        <span>正在检查当前学年...</span>
      </div>
    );
  }

  if (!hasCurrentAcademicYear) {
    if (role === 'ADMIN') {
      return <Navigate to={ACADEMIC_YEAR_PATH} replace state={{ from: location.pathname }} />;
    }

    return <MissingAcademicYearNotice role={role} />;
  }

  return children;
}

export default AcademicYearGuard;
