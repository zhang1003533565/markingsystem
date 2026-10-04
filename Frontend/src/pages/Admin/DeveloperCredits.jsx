import { useMemo, useState } from 'react';
import {
  AppstoreOutlined,
  BulbOutlined,
  CrownOutlined,
  HeartOutlined,
  IdcardOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  TrophyOutlined,
} from '../../components/AcademicIcons';
import './DeveloperCredits.css';

const contributorGroups = [
  {
    key: 'frontend',
    title: '前端贡献者',
    tag: '前端开发',
    icon: <BulbOutlined style={{ fontSize: 22 }} />,
    accent: 'blue',
    placeholderSummary: '负责页面实现、交互体验与视觉还原。',
  },
  {
    key: 'backend',
    title: '后端贡献者',
    tag: '后端开发',
    icon: <SafetyCertificateOutlined style={{ fontSize: 22 }} />,
    accent: 'green',
    placeholderSummary: '负责接口服务、数据逻辑与系统稳定。',
  },
  {
    key: 'product',
    title: '产品贡献者',
    tag: '产品协作',
    icon: <AppstoreOutlined style={{ fontSize: 22 }} />,
    accent: 'indigo',
    placeholderSummary: '负责需求梳理、流程优化与体验建议。',
  },
  {
    key: 'testing',
    title: '测试贡献者',
    tag: '测试支持',
    icon: <TeamOutlined style={{ fontSize: 22 }} />,
    accent: 'orange',
    placeholderSummary: '负责功能验证、问题反馈与质量把关。',
  },
];

const developerPhotos2026 = {
  zhaoYuting: new URL('../../assets/开发者/2026年/赵雨婷.png', import.meta.url).href,
  duanHanrui: new URL('../../assets/开发者/2026年/段涵蕊.png', import.meta.url).href,
  zhangZesheng: new URL('../../assets/开发者/2026年/张泽升.jpg', import.meta.url).href,
  liQingrui: new URL('../../assets/开发者/2026年/李卿瑞.jpg', import.meta.url).href,
  liuZhiting: new URL('../../assets/开发者/2026年/刘治廷.png', import.meta.url).href,
  wangShasha: new URL('../../assets/开发者/2026年/王莎莎.jpg', import.meta.url).href,
  suYan: new URL('../../assets/开发者/2026年/苏岩.jpg', import.meta.url).href,
  zhangYixuan: new URL('../../assets/开发者/2026年/张艺轩.png', import.meta.url).href,
};

const contributorDataByYear = {
  2026: {
    frontend: [
      {
        id: '2026-frontend-zhao-yuting',
        name: '赵雨婷',
        className: '数据241',
        roleTag: '前端开发',
        slogan: '航程既定，步履不停',
        summary: '航程既定，步履不停',
        photo: developerPhotos2026.zhaoYuting,
      },
      {
        id: '2026-frontend-duan-hanrui',
        name: '段涵蕊',
        className: '网络251',
        roleTag: '前端开发',
        slogan: '继续追，谁的光荣不是伴着眼泪',
        summary: '继续追，谁的光荣不是伴着眼泪',
        photo: developerPhotos2026.duanHanrui,
      },
    ],
    backend: [
      {
        id: '2026-backend-zhang-zesheng',
        name: '张泽升',
        className: '计科231',
        roleTag: '后端开发',
        slogan: '没有天赋，那就重复',
        summary: '没有天赋，那就重复',
        photo: developerPhotos2026.zhangZesheng,
      },
      {
        id: '2026-backend-li-qingrui',
        name: '李卿瑞',
        className: '数据241',
        roleTag: '后端开发',
        slogan: '做永远学习的开发者',
        summary: '做永远学习的开发者',
        photo: developerPhotos2026.liQingrui,
      },
      {
        id: '2026-backend-liu-zhiting',
        name: '刘治廷',
        className: '数据252',
        roleTag: '后端开发',
        slogan: '放空思想，无形无相，无限进步',
        summary: '放空思想，无形无相，无限进步',
        photo: developerPhotos2026.liuZhiting,
      },
    ],
    product: [
      {
        id: '2026-product-wang-shasha',
        name: '王莎莎',
        className: '计科241',
        roleTag: '产品协作',
        slogan: '行动才能取得荣耀，但是大部分伟大成就起源于静止。',
        summary: '行动才能取得荣耀，但是大部分伟大成就起源于静止。',
        photo: developerPhotos2026.wangShasha,
      },
    ],
    testing: [
      {
        id: '2026-testing-su-yan',
        name: '苏岩',
        className: '数据241',
        roleTag: '测试支持',
        slogan: '万事尽力而为，得失自在随缘',
        summary: '万事尽力而为，得失自在随缘',
        photo: developerPhotos2026.suYan,
      },
      {
        id: '2026-testing-zhang-yixuan',
        name: '张艺轩',
        className: '计科253',
        roleTag: '测试支持',
        slogan: '认真验证每一步，让系统更可靠。',
        summary: '参与测试用例执行、问题复现与回归验证。',
        photo: developerPhotos2026.zhangYixuan,
      },
    ],
  },
  2027: {
    frontend: [],
    backend: [],
    product: [],
    testing: [],
  },
};

const contributorYears = Object.keys(contributorDataByYear).sort((left, right) => Number(right) - Number(left));
const defaultContributorYear = contributorDataByYear['2026'] ? '2026' : contributorYears[0];
const emptyContributorYearData = {
  frontend: [],
  backend: [],
  product: [],
  testing: [],
};
const minimumHonorSlots = 5;
const topContributorCount = 3;

function buildPendingSlot(group, index) {
  return {
    id: `${group.key}-pending-slot-${index}`,
    name: '期待加入',
    className: '期待加入',
    roleTag: group.tag,
    slogan: '期待新的伙伴加入，一起完善系统。',
    summary: '期待新的伙伴加入，一起完善系统。',
    photo: '',
    pending: true,
  };
}

function buildDisplayContributors(group, contributors = []) {
  if (!contributors.length) {
    return [];
  }

  const displayContributors = [...contributors];
  while (displayContributors.length < minimumHonorSlots) {
    displayContributors.push(buildPendingSlot(group, displayContributors.length + 1));
  }
  return displayContributors.map((contributor, index) => ({
    ...contributor,
    rank: index + 1,
    roleTag: contributor.roleTag || group.tag,
    summary: contributor.summary || contributor.slogan || group.placeholderSummary,
  }));
}

function ContributorPhoto({ contributor }) {
  if (contributor.photo) {
    return (
      <img
        src={contributor.photo}
        alt={`${contributor.name} 的证件照`}
        className="developer-contributor-photo-img"
      />
    );
  }

  return (
    <div className="developer-contributor-photo-fallback" aria-label="期待加入">
      <IdcardOutlined style={{ fontSize: 34 }} />
      <span>证件照</span>
    </div>
  );
}

function FeaturedContributorCard({ contributor }) {
  const rankClassName = contributor.rank <= topContributorCount ? `rank-${contributor.rank}` : '';

  return (
    <article className={`developer-featured-card ${rankClassName}`}>
      <div className="developer-featured-photo-frame">
        <ContributorPhoto contributor={contributor} />
        <span className="developer-rank-medal">
          {contributor.rank}
        </span>
        {contributor.pending ? (
          <span className="developer-photo-state">期待加入</span>
        ) : null}
      </div>
      <div className="developer-featured-body">
        <h3>{contributor.name}</h3>
        <span className="developer-person-class">{contributor.className}</span>
        <p>{contributor.summary}</p>
      </div>
    </article>
  );
}

function CompactContributorCard({ contributor }) {
  return (
    <article className="developer-compact-card">
      <div className="developer-compact-photo">
        <ContributorPhoto contributor={contributor} />
      </div>
      <div>
        <h3>{contributor.name}</h3>
        <span className="developer-person-class">{contributor.className}</span>
        <p>{contributor.summary}</p>
      </div>
    </article>
  );
}

function DeveloperCredits() {
  const [selectedYear, setSelectedYear] = useState(defaultContributorYear);
  const selectedYearData = useMemo(
    () => contributorDataByYear[selectedYear] || emptyContributorYearData,
    [selectedYear],
  );

  return (
    <div className="developer-credits-page">
      <section className="developer-credits-hero">
        <div className="developer-credits-hero-copy">
          <span className="developer-credits-kicker">Acknowledgement</span>
          <h1>{selectedYear} 年开发者致谢</h1>
          <p>感谢参与系统建设、测试、反馈与维护的每一位同学和老师。</p>
        </div>
        <div className="developer-credits-year-panel">
          <span>致谢年份</span>
          <div className="developer-credits-year-switch" role="tablist" aria-label="致谢年份">
            {contributorYears.map((year) => (
              <button
                key={year}
                type="button"
                className={`developer-credits-year-btn ${year === selectedYear ? 'active' : ''}`}
                onClick={() => setSelectedYear(year)}
              >
                {year}
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="developer-honor-board">
        <div className="developer-honor-board-inner">
          <div className="developer-honor-board-title">
            <CrownOutlined style={{ fontSize: 25 }} />
            <div>
              <h2>光荣榜</h2>
              <p>致敬每一份付出，汇聚点滴力量，成就更好的系统</p>
            </div>
            <HeartOutlined style={{ fontSize: 23 }} />
          </div>

          <div className="developer-honor-grid">
            {contributorGroups.map((group) => {
              const contributors = buildDisplayContributors(group, selectedYearData[group.key] || []);
              const featuredContributors = contributors.slice(0, topContributorCount);
              const compactContributors = contributors.slice(topContributorCount);
              const groupRealCount = (selectedYearData[group.key] || []).filter((item) => !item.pending).length;

              return (
                <section
                  key={group.key}
                  className={`developer-honor-column developer-honor-column-${group.accent}`}
                >
                  <header className="developer-honor-column-header">
                    <span className="developer-honor-icon">{group.icon}</span>
                    <div>
                      <h3>{group.title}</h3>
                      <p>{group.tag}</p>
                    </div>
                    <strong>{groupRealCount ? `${groupRealCount} 位` : '期待加入'}</strong>
                  </header>

                  {contributors.length ? (
                    <>
                      <div className="developer-featured-grid">
                        {featuredContributors.map((contributor) => (
                          <FeaturedContributorCard
                            key={contributor.id}
                            contributor={contributor}
                          />
                        ))}
                      </div>
                      {compactContributors.length ? (
                        <div className="developer-compact-list">
                          {compactContributors.map((contributor) => (
                            <CompactContributorCard
                              key={contributor.id}
                              contributor={contributor}
                            />
                          ))}
                        </div>
                      ) : null}
                      <button type="button" className="developer-view-all-btn">
                        查看全部 {contributors.length} 个荣誉席位
                      </button>
                    </>
                  ) : (
                    <div className="developer-credits-empty">
                      <IdcardOutlined style={{ fontSize: 24 }} />
                      <span>{selectedYear} 年暂无{group.title}信息</span>
                    </div>
                  )}
                </section>
              );
            })}
          </div>

          <div className="developer-honor-footer">
            <TrophyOutlined style={{ fontSize: 24 }} />
            <span>代码改变世界，协作成就未来。感谢每一位贡献者的热忱与努力！</span>
          </div>
        </div>
      </section>
    </div>
  );
}

export default DeveloperCredits;
