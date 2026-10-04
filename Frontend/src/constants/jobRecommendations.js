// 当后端尚未生成/返回岗位推荐时，用于前端占位展示的具体岗位示例。
// 后端有真实数据后会优先使用真实数据替换。
export const DEFAULT_JOB_RECOMMENDATIONS = [
  {
    id: 'default-job-1',
    jobTitle: 'Java 后端开发工程师（Spring Cloud）',
    salary: '15k-30k',
    skills: 'Java, Spring Boot, MySQL, Redis, Kafka',
    recruitmentLink: 'https://www.zhipin.com/web/geek/job?query=Java%E5%90%8E%E7%AB%AF%E5%BC%80%E5%8F%91%E5%B7%A5%E7%A8%8B%E5%B8%88',
    source: '示例数据',
  },
  {
    id: 'default-job-2',
    jobTitle: '前端开发工程师（React/Vue）',
    salary: '14k-28k',
    skills: 'JavaScript, TypeScript, React, Vue, Webpack',
    recruitmentLink: 'https://www.zhipin.com/web/geek/job?query=%E5%89%8D%E7%AB%AF%E5%BC%80%E5%8F%91%E5%B7%A5%E7%A8%8B%E5%B8%88',
    source: '示例数据',
  },
  {
    id: 'default-job-3',
    jobTitle: '测试开发工程师（自动化测试）',
    salary: '12k-25k',
    skills: 'Java/Python, Selenium, Pytest, CI/CD, 接口测试',
    recruitmentLink: 'https://www.zhipin.com/web/geek/job?query=%E6%B5%8B%E8%AF%95%E5%BC%80%E5%8F%91%E5%B7%A5%E7%A8%8B%E5%B8%88',
    source: '示例数据',
  },
  {
    id: 'default-job-4',
    jobTitle: 'DevOps 工程师（云原生）',
    salary: '18k-35k',
    skills: 'Kubernetes, Docker, Linux, CI/CD, Go',
    recruitmentLink: 'https://www.zhipin.com/web/geek/job?query=DevOps%E5%B7%A5%E7%A8%8B%E5%B8%88',
    source: '示例数据',
  },
  {
    id: 'default-job-5',
    jobTitle: '全栈开发工程师（React+Node.js）',
    salary: '18k-35k',
    skills: 'React, Node.js, TypeScript, MongoDB, AWS',
    recruitmentLink: 'https://www.zhipin.com/web/geek/job?query=%E5%85%A8%E6%A0%88%E5%BC%80%E5%8F%91%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88React%2BNode.js%EF%BC%89',
    source: '示例数据',
  },
];
