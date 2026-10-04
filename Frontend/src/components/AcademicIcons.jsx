import AppIcon from './AppIcon';

const createIcon = (name) => function AcademicIcon({ style, className }) {
  const mergedStyle = {
    verticalAlign: '-0.125em',
    ...style
  };
  const classes = ['anticon', 'app-icon', className].filter(Boolean).join(' ');

  return <AppIcon name={name} size={mergedStyle?.fontSize || 18} className={classes} style={mergedStyle} />;
};

export const AppstoreOutlined = createIcon('appstoreOutlined');
export const ArrowDownOutlined = createIcon('arrowDownOutlined');
export const ArrowUpOutlined = createIcon('arrowUpOutlined');
export const BankOutlined = createIcon('bankOutlined');
export const BarChartOutlined = createIcon('barChartOutlined');
export const BellOutlined = createIcon('bellOutlined');
export const BookFilled = createIcon('bookFilled');
export const BookOutlined = createIcon('bookOutlined');
export const BulbOutlined = createIcon('bulbOutlined');
export const CalendarOutlined = createIcon('calendarOutlined');
export const CheckCircleOutlined = createIcon('checkCircleOutlined');
export const CheckOutlined = createIcon('checkOutlined');
export const ClockCircleOutlined = createIcon('clockCircleOutlined');
export const CloseCircleOutlined = createIcon('closeCircleOutlined');
export const CrownOutlined = createIcon('crownOutlined');
export const DeleteOutlined = createIcon('deleteOutlined');
export const DownloadOutlined = createIcon('downloadOutlined');
export const EditOutlined = createIcon('editOutlined');
export const EyeOutlined = createIcon('eyeOutlined');
export const FileOutlined = createIcon('fileOutlined');
export const FileTextOutlined = createIcon('fileTextOutlined');
export const FlagOutlined = createIcon('flagOutlined');
export const FormOutlined = createIcon('formOutlined');
export const FundOutlined = createIcon('fundOutlined');
export const HeartOutlined = createIcon('heartOutlined');
export const HistoryOutlined = createIcon('historyOutlined');
export const HomeOutlined = createIcon('homeOutlined');
export const IdcardOutlined = createIcon('idcardOutlined');
export const InfoCircleOutlined = createIcon('infoCircleOutlined');
export const LineChartOutlined = createIcon('lineChartOutlined');
export const LinkOutlined = createIcon('linkOutlined');
export const LockOutlined = createIcon('lockOutlined');
export const LogoutOutlined = createIcon('logoutOutlined');
export const MailOutlined = createIcon('mailOutlined');
export const MobileOutlined = createIcon('mobileOutlined');
export const NotificationOutlined = createIcon('notificationOutlined');
export const OrderedListOutlined = createIcon('orderedListOutlined');
export const PercentageOutlined = createIcon('percentageOutlined');
export const PieChartOutlined = createIcon('pieChartOutlined');
export const PlusCircleOutlined = createIcon('plusCircleOutlined');
export const RadarChartOutlined = createIcon('radarChartOutlined');
export const ReloadOutlined = createIcon('reloadOutlined');
export const RiseOutlined = createIcon('riseOutlined');
export const RollbackOutlined = createIcon('rollbackOutlined');
export const SafetyCertificateOutlined = createIcon('safetyCertificateOutlined');
export const SearchOutlined = createIcon('searchOutlined');
export const SettingOutlined = createIcon('settingOutlined');
export const StarOutlined = createIcon('starOutlined');
export const TableOutlined = createIcon('tableOutlined');
export const TeamOutlined = createIcon('teamOutlined');
export const ThunderboltOutlined = createIcon('thunderboltOutlined');
export const TrophyOutlined = createIcon('trophyOutlined');
export const UploadOutlined = createIcon('uploadOutlined');
export const UserOutlined = createIcon('userOutlined');
export const WarningOutlined = createIcon('warningOutlined');
