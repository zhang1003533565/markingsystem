import { useMemo, useRef, forwardRef } from 'react';
import { Modal, Button, message } from 'antd';

const COLORS = [
  { bg: '#eef4ff', line: '#2563eb', text: '#1e40af' },
  { bg: '#ecfdf5', line: '#16a34a', text: '#166534' },
  { bg: '#f5f0ff', line: '#9333ea', text: '#6b21a8' },
  { bg: '#fff7ed', line: '#ea580c', text: '#9a3412' },
  { bg: '#ecfeff', line: '#0891b2', text: '#155e75' },
];

/**
 * 技能学习建议：label 展示文案，url 为可跳转外链（官方文档 / 教程优先）
 * 每项尽量给 2–3 条具体资源，避免空泛描述
 */
const LEARNING_TIPS = {
  python: [
    { label: 'Python 官方文档（中文）', url: 'https://docs.python.org/zh-cn/3/' },
    { label: 'LeetCode 刷题（Python）', url: 'https://leetcode.cn/problemset/' },
    { label: '廖雪峰 Python 教程', url: 'https://www.liaoxuefeng.com/wiki/1016959663602400' },
  ],
  java: [
    { label: 'Spring 官方文档', url: 'https://spring.io/projects/spring-boot' },
    { label: 'Java Tutorial（Oracle）', url: 'https://docs.oracle.com/javase/tutorial/' },
    { label: 'LeetCode 刷题（Java）', url: 'https://leetcode.cn/problemset/' },
  ],
  go: [
    { label: 'Go by Example', url: 'https://gobyexample.com/' },
    { label: 'Go 官方 Tour', url: 'https://go.dev/tour/' },
    { label: 'Go 标准库文档', url: 'https://pkg.go.dev/std' },
  ],
  'c++': [
    { label: 'cppreference 手册', url: 'https://zh.cppreference.com/w/' },
    { label: 'LeetCode 刷题（C++）', url: 'https://leetcode.cn/problemset/' },
    { label: 'C++ Core Guidelines', url: 'https://isocpp.github.io/CppCoreGuidelines/CppCoreGuidelines' },
  ],
  cpp: [
    { label: 'cppreference 手册', url: 'https://zh.cppreference.com/w/' },
    { label: 'LeetCode 刷题（C++）', url: 'https://leetcode.cn/problemset/' },
  ],
  javascript: [
    { label: 'MDN JavaScript 文档', url: 'https://developer.mozilla.org/zh-CN/docs/Web/JavaScript' },
    { label: '现代 JavaScript 教程', url: 'https://zh.javascript.info/' },
    { label: 'You Don\'t Know JS（GitHub）', url: 'https://github.com/getify/You-Dont-Know-JS' },
  ],
  typescript: [
    { label: 'TypeScript 官方手册', url: 'https://www.typescriptlang.org/docs/handbook/intro.html' },
    { label: 'TS 演练场 Playground', url: 'https://www.typescriptlang.org/play' },
  ],
  arkts: [
    { label: 'ArkTS 语言基础（华为文档）', url: 'https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/arkts-get-started' },
    { label: 'HarmonyOS 应用开发入门', url: 'https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/application-dev-guide' },
  ],
  鸿蒙: [
    { label: 'HarmonyOS 开发者文档', url: 'https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/application-dev-guide' },
    { label: 'API 参考中心', url: 'https://developer.huawei.com/consumer/cn/doc/harmonyos-references/development-intro-api' },
  ],
  鸿蒙api: [
    { label: 'HarmonyOS API 参考', url: 'https://developer.huawei.com/consumer/cn/doc/harmonyos-references/development-intro-api' },
    { label: '应用开发指南', url: 'https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/application-dev-guide' },
  ],
  移动开发: [
    { label: 'Android 开发者文档', url: 'https://developer.android.google.cn/docs' },
    { label: 'Flutter 中文文档', url: 'https://docs.flutter.cn/' },
    { label: 'HarmonyOS 应用开发', url: 'https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/application-dev-guide' },
  ],
  android: [
    { label: 'Android 官方文档', url: 'https://developer.android.google.cn/docs' },
    { label: 'Kotlin 官方文档', url: 'https://kotlinlang.org/docs/home.html' },
  ],
  ios: [
    { label: 'Apple 开发者文档', url: 'https://developer.apple.com/documentation/' },
    { label: 'Swift 官方文档', url: 'https://docs.swift.org/swift-book/' },
  ],
  flutter: [
    { label: 'Flutter 中文文档', url: 'https://docs.flutter.cn/' },
    { label: 'Flutter Cookbook', url: 'https://docs.flutter.cn/cookbook' },
  ],
  react: [
    { label: 'React 官方文档', url: 'https://react.dev/learn' },
    { label: 'React 中文网（社区）', url: 'https://zh-hans.react.dev/learn' },
  ],
  vue: [
    { label: 'Vue 3 官方文档', url: 'https://cn.vuejs.org/guide/introduction.html' },
    { label: 'Vue Router 文档', url: 'https://router.vuejs.org/zh/' },
  ],
  node: [
    { label: 'Node.js 官方文档', url: 'https://nodejs.org/docs/latest/api/' },
    { label: 'Express 指南', url: 'https://expressjs.com/zh-cn/guide/routing.html' },
  ],
  rust: [
    { label: 'The Rust Book（中文）', url: 'https://kaisery.github.io/trpl-zh-cn/' },
    { label: 'Rustlings 练习', url: 'https://github.com/rust-lang/rustlings' },
  ],
  pytorch: [
    { label: 'PyTorch 官方 Tutorials', url: 'https://pytorch.org/tutorials/' },
    { label: '动手学深度学习', url: 'https://zh.d2l.ai/' },
  ],
  tensorflow: [
    { label: 'TensorFlow 官方指南', url: 'https://www.tensorflow.org/tutorials' },
    { label: 'Keras 入门', url: 'https://keras.io/getting_started/' },
  ],
  深度学习: [
    { label: '动手学深度学习（李沐）', url: 'https://zh.d2l.ai/' },
    { label: 'PyTorch 官方教程', url: 'https://pytorch.org/tutorials/' },
    { label: 'CS231n 课程主页', url: 'http://cs231n.stanford.edu/' },
  ],
  机器学习: [
    { label: '吴恩达 Machine Learning', url: 'https://www.coursera.org/learn/machine-learning' },
    { label: 'scikit-learn 用户指南', url: 'https://scikit-learn.org/stable/user_guide.html' },
  ],
  计算机视觉: [
    { label: 'OpenCV 官方文档', url: 'https://docs.opencv.org/' },
    { label: 'CS231n 视觉课程', url: 'http://cs231n.stanford.edu/' },
    { label: 'PyTorch Vision 教程', url: 'https://pytorch.org/vision/stable/index.html' },
  ],
  opencv: [
    { label: 'OpenCV 官方文档', url: 'https://docs.opencv.org/' },
    { label: 'OpenCV-Python Tutorials', url: 'https://docs.opencv.org/4.x/d6/d00/tutorial_py_root.html' },
  ],
  nlp: [
    { label: 'Hugging Face 课程', url: 'https://huggingface.co/learn/nlp-course/zh-CN/chapter1/1' },
    { label: 'spaCy 使用指南', url: 'https://spacy.io/usage' },
  ],
  llm: [
    { label: 'Hugging Face Transformers', url: 'https://huggingface.co/docs/transformers/index' },
    { label: 'LangChain 文档', url: 'https://python.langchain.com/docs/get_started/introduction' },
    { label: '吴恩达 Generative AI', url: 'https://www.deeplearning.ai/short-courses/' },
  ],
  rag: [
    { label: 'LangChain RAG 教程', url: 'https://python.langchain.com/docs/tutorials/rag/' },
    { label: 'LlamaIndex 文档', url: 'https://docs.llamaindex.ai/' },
  ],
  cnn: [
    { label: 'CS231n 卷积网络', url: 'https://cs231n.github.io/convolutional-networks/' },
    { label: 'PyTorch CNN 示例', url: 'https://pytorch.org/tutorials/beginner/blitz/cifar10_tutorial.html' },
  ],
  transformer: [
    { label: 'Attention Is All You Need', url: 'https://arxiv.org/abs/1706.03762' },
    { label: 'Hugging Face 课程', url: 'https://huggingface.co/learn/nlp-course/zh-CN/chapter1/1' },
  ],
  微调: [
    { label: 'PEFT / LoRA 文档', url: 'https://huggingface.co/docs/peft/index' },
    { label: 'Transformers Trainer', url: 'https://huggingface.co/docs/transformers/train' },
  ],
  docker: [
    { label: 'Docker 官方文档', url: 'https://docs.docker.com/get-started/' },
    { label: 'Play with Docker', url: 'https://labs.play-with-docker.com/' },
  ],
  kubernetes: [
    { label: 'Kubernetes 官方文档', url: 'https://kubernetes.io/zh-cn/docs/home/' },
    { label: 'K8s 中文社区', url: 'https://www.kubernetes.org.cn/docs' },
  ],
  k8s: [
    { label: 'Kubernetes 官方文档', url: 'https://kubernetes.io/zh-cn/docs/home/' },
    { label: 'Minikube 入门', url: 'https://minikube.sigs.k8s.io/docs/start/' },
  ],
  istio: [
    { label: 'Istio 官方文档', url: 'https://istio.io/latest/zh/docs/' },
    { label: '流量管理指南', url: 'https://istio.io/latest/zh/docs/tasks/traffic-management/' },
  ],
  flink: [
    { label: 'Flink 官方文档', url: 'https://nightlies.apache.org/flink/flink-docs-stable/' },
    { label: 'Flink 中文社区', url: 'https://flink.apache.org/zh/' },
  ],
  spark: [
    { label: 'Spark 官方文档', url: 'https://spark.apache.org/docs/latest/' },
    { label: 'Spark SQL 指南', url: 'https://spark.apache.org/docs/latest/sql-programming-guide.html' },
  ],
  kafka: [
    { label: 'Kafka 官方文档', url: 'https://kafka.apache.org/documentation/' },
    { label: 'Kafka 快速入门', url: 'https://kafka.apache.org/quickstart' },
  ],
  hadoop: [
    { label: 'Hadoop 官方文档', url: 'https://hadoop.apache.org/docs/current/' },
    { label: 'HDFS 用户指南', url: 'https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-hdfs/HdfsUserGuide.html' },
  ],
  mysql: [
    { label: 'MySQL 官方手册', url: 'https://dev.mysql.com/doc/' },
    { label: 'SQL 教程（菜鸟）', url: 'https://www.runoob.com/sql/sql-tutorial.html' },
  ],
  redis: [
    { label: 'Redis 官方文档', url: 'https://redis.io/docs/' },
    { label: 'Redis 命令参考', url: 'https://redis.io/commands/' },
  ],
  mongodb: [
    { label: 'MongoDB 官方手册', url: 'https://www.mongodb.com/docs/manual/' },
    { label: 'MongoDB University', url: 'https://learn.mongodb.com/' },
  ],
  ffmpeg: [
    { label: 'FFmpeg 官方文档', url: 'https://ffmpeg.org/documentation.html' },
    { label: 'FFmpeg Wiki', url: 'https://trac.ffmpeg.org/wiki' },
  ],
  webrtc: [
    { label: 'WebRTC 官方文档', url: 'https://webrtc.org/getting-started/overview' },
    { label: 'MDN WebRTC API', url: 'https://developer.mozilla.org/zh-CN/docs/Web/API/WebRTC_API' },
  ],
  云原生: [
    { label: 'CNCF Landscape', url: 'https://landscape.cncf.io/' },
    { label: 'Kubernetes 文档', url: 'https://kubernetes.io/zh-cn/docs/home/' },
  ],
  容器化: [
    { label: 'Docker 入门', url: 'https://docs.docker.com/get-started/' },
    { label: 'Kubernetes 入门', url: 'https://kubernetes.io/zh-cn/docs/tutorials/kubernetes-basics/' },
  ],
  分布式: [
    { label: 'MIT 6.824 课程主页', url: 'https://pdos.csail.mit.edu/6.824/' },
    { label: 'DDIA 配套资料', url: 'https://dataintensive.net/' },
  ],
  微服务: [
    { label: 'Spring Cloud 文档', url: 'https://spring.io/projects/spring-cloud' },
    { label: '微服务模式（microservices.io）', url: 'https://microservices.io/patterns/index.html' },
  ],
  实时计算: [
    { label: 'Flink 官方文档', url: 'https://nightlies.apache.org/flink/flink-docs-stable/' },
    { label: 'Kafka Streams', url: 'https://kafka.apache.org/documentation/streams/' },
  ],
  流处理: [
    { label: 'Flink 流处理指南', url: 'https://nightlies.apache.org/flink/flink-docs-stable/' },
    { label: 'Kafka Streams', url: 'https://kafka.apache.org/documentation/streams/' },
  ],
  自动驾驶: [
    { label: 'Apollo 开源平台', url: 'https://apollo.auto/' },
    { label: 'CARLA 模拟器', url: 'https://carla.org/' },
  ],
  '传感器融合': [
    { label: 'Kalman Filter 讲解', url: 'https://www.kalmanfilter.net/default.aspx' },
    { label: 'Apollo 感知模块', url: 'https://apollo.auto/' },
  ],
  '点云处理': [
    { label: 'PCL 官方文档', url: 'https://pointclouds.org/documentation/' },
    { label: 'Open3D 文档', url: 'http://www.open3d.org/docs/release/' },
  ],
  编解码: [
    { label: 'FFmpeg 文档', url: 'https://ffmpeg.org/documentation.html' },
    { label: 'H.264 概述（MDN）', url: 'https://developer.mozilla.org/zh-CN/docs/Web/Media/Formats/Video_codecs' },
  ],
  h264: [
    { label: 'MDN 视频编解码', url: 'https://developer.mozilla.org/zh-CN/docs/Web/Media/Formats/Video_codecs' },
    { label: 'FFmpeg H.264 指南', url: 'https://trac.ffmpeg.org/wiki/Encode/H.264' },
  ],
  h265: [
    { label: 'FFmpeg H.265 指南', url: 'https://trac.ffmpeg.org/wiki/Encode/H.265' },
    { label: 'MDN 视频编解码', url: 'https://developer.mozilla.org/zh-CN/docs/Web/Media/Formats/Video_codecs' },
  ],
  linux: [
    { label: 'Linux 命令手册', url: 'https://www.kernel.org/doc/html/latest/' },
    { label: '鸟哥的 Linux 私房菜', url: 'https://linux.vbird.org/' },
  ],
  git: [
    { label: 'Pro Git 中文版', url: 'https://git-scm.com/book/zh/v2' },
    { label: 'GitHub Docs', url: 'https://docs.github.com/zh' },
  ],
  算法: [
    { label: 'LeetCode 题库', url: 'https://leetcode.cn/problemset/' },
    { label: 'OI Wiki 算法', url: 'https://oi-wiki.org/' },
  ],
  数据结构: [
    { label: 'OI Wiki 数据结构', url: 'https://oi-wiki.org/ds/' },
    { label: '可视化数据结构', url: 'https://visualgo.net/zh' },
  ],
};

function normalizeTips(raw) {
  if (!raw?.length) return null;
  return raw.map((item) => {
    if (typeof item === 'string') return { label: item, url: '' };
    return { label: String(item.label || '').trim(), url: String(item.url || '').trim() };
  }).filter((item) => item.label);
}

function getLearningTips(skill) {
  const key = skill.toLowerCase().trim();
  const direct = LEARNING_TIPS[key];
  if (direct) return normalizeTips(direct);

  const fuzzyKey = Object.keys(LEARNING_TIPS).find((k) => key.includes(k) || k.includes(key));
  if (fuzzyKey) return normalizeTips(LEARNING_TIPS[fuzzyKey]);

  // 兜底：给可跳转的检索入口，避免右侧空白
  const q = encodeURIComponent(skill);
  return normalizeTips([
    { label: `掘金搜索「${skill}」`, url: `https://juejin.cn/search?query=${q}&type=0` },
    { label: `MDN / 文档检索`, url: `https://developer.mozilla.org/zh-CN/search?q=${q}` },
  ]);
}

const NODE_W = 220;
const NODE_H = 50;
const TIP_W = 268;
const TIP_H = 24;
const SKILL_W = 110;
const SKILL_H = 28;
const GAP_X = 48;
const MARGIN = 30;
const BRANCH_GAP = 32;
const ROOT_W = 145;
const ROOT_H = 58;
const MAP_BG = '#fafbfc';

function calcLayout(branches, rootY) {
  let y = MARGIN;

  const result = branches.map((b) => {
    const startY = y;
    let skillAreaH = 0;
    let maxTips = 0;
    if (b.skills.length > 0) {
      maxTips = Math.max(...b.skills.map((s) => (s.tips ? s.tips.length : 0)), 0);
      skillAreaH = b.skills.reduce((sum, s) => {
        const rows = Math.max(1, s.tips ? s.tips.length : 0);
        return sum + Math.max(SKILL_H + 8, rows * (TIP_H + 4) + 4);
      }, 8);
    }

    const branchH = NODE_H + skillAreaH;
    y = startY + branchH + BRANCH_GAP;

    return {
      ...b,
      nodeY: startY,
      branchH,
      skillAreaH,
      maxTips,
      maxW: NODE_W + GAP_X + (b.skills.length > 0 ? SKILL_W : 0) + (maxTips > 0 ? GAP_X + TIP_W : 0),
    };
  });

  const totalH = Math.max(y + MARGIN, 400);
  const computedRootY = Math.max(MARGIN, totalH / 2 - ROOT_H / 2);
  const actualRootY = rootY != null ? rootY : computedRootY;
  const nodeX = MARGIN + ROOT_W + 60;
  const maxBranchW = Math.max(...result.map((b) => b.maxW), 0);
  const totalW = nodeX + maxBranchW + MARGIN + 40;

  return { branches: result, totalH, totalW, rootY: actualRootY, nodeX };
}

function buildSaveFilename(focusSkill) {
  const safe = String(focusSkill || '')
    .trim()
    .replace(/[\\/:*?"<>|]/g, '_')
    .slice(0, 40);
  return safe ? `岗位技能思维导图-${safe}.png` : '岗位技能思维导图.png';
}

async function downloadSvgAsPng(svgEl, filename) {
  if (!svgEl) throw new Error('SVG not found');

  const clone = svgEl.cloneNode(true);
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  if (!clone.getAttribute('xmlns:xlink')) {
    clone.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
  }

  const vb = svgEl.viewBox?.baseVal;
  const w = Math.max(1, Math.round(vb?.width || svgEl.clientWidth || 800));
  const h = Math.max(1, Math.round(vb?.height || svgEl.clientHeight || 600));
  clone.setAttribute('width', String(w));
  clone.setAttribute('height', String(h));
  clone.style.background = MAP_BG;

  const svgStr = new XMLSerializer().serializeToString(clone);
  const svgBlob = new Blob([svgStr], { type: 'image/svg+xml;charset=utf-8' });
  const svgUrl = URL.createObjectURL(svgBlob);

  try {
    const img = await new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve(image);
      image.onerror = () => reject(new Error('SVG image load failed'));
      image.src = svgUrl;
    });

    const scale = 2;
    const canvas = document.createElement('canvas');
    canvas.width = w * scale;
    canvas.height = h * scale;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas unsupported');

    ctx.fillStyle = MAP_BG;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
    ctx.drawImage(img, 0, 0, w, h);

    await new Promise((resolve, reject) => {
      canvas.toBlob((pngBlob) => {
        if (!pngBlob) {
          reject(new Error('PNG export failed'));
          return;
        }
        const pngUrl = URL.createObjectURL(pngBlob);
        const a = document.createElement('a');
        a.href = pngUrl;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(pngUrl);
        resolve();
      }, 'image/png');
    });
  } finally {
    URL.revokeObjectURL(svgUrl);
  }
}

function TipNode({ tip, tipX, tipY, skillX, skillY, skillBlockH }) {
  const hasLink = Boolean(tip.url);
  const label = tip.label.length > 28 ? `${tip.label.slice(0, 26)}…` : tip.label;
  const fill = hasLink ? '#eff6ff' : '#f8fafc';
  const stroke = hasLink ? '#bfdbfe' : '#e2e8f0';
  const textColor = hasLink ? '#1d4ed8' : '#64748b';
  const prefix = hasLink ? '🔗 ' : '📖 ';

  const body = (
    <>
      <line
        x1={skillX + SKILL_W}
        y1={skillY + (skillBlockH - SKILL_H) / 2 + SKILL_H / 2}
        x2={skillX + SKILL_W + GAP_X / 2}
        y2={skillY + (skillBlockH - SKILL_H) / 2 + SKILL_H / 2}
        stroke="#cbd5e1"
        strokeWidth="1"
        opacity="0.4"
        strokeDasharray="3,3"
      />
      <line
        x1={skillX + SKILL_W + GAP_X / 2}
        y1={skillY + (skillBlockH - SKILL_H) / 2 + SKILL_H / 2}
        x2={skillX + SKILL_W + GAP_X / 2}
        y2={tipY + TIP_H / 2}
        stroke="#cbd5e1"
        strokeWidth="1"
        opacity="0.4"
        strokeDasharray="3,3"
      />
      <line
        x1={skillX + SKILL_W + GAP_X / 2}
        y1={tipY + TIP_H / 2}
        x2={tipX}
        y2={tipY + TIP_H / 2}
        stroke="#cbd5e1"
        strokeWidth="1"
        opacity="0.4"
        strokeDasharray="3,3"
      />
      <rect
        x={tipX}
        y={tipY}
        width={TIP_W}
        height={TIP_H}
        rx="4"
        fill={fill}
        stroke={stroke}
        strokeWidth="1"
        style={hasLink ? { cursor: 'pointer' } : undefined}
      />
      <text
        x={tipX + 8}
        y={tipY + TIP_H / 2}
        dominantBaseline="central"
        fill={textColor}
        fontSize="10"
        fontWeight={hasLink ? 600 : 400}
        style={hasLink ? { cursor: 'pointer', textDecoration: 'underline' } : undefined}
      >
        {prefix}{label}
      </text>
      {hasLink && (
        <title>{`${tip.label}\n点击打开：${tip.url}`}</title>
      )}
    </>
  );

  if (!hasLink) {
    return <g>{body}</g>;
  }

  return (
    <a href={tip.url} target="_blank" rel="noopener noreferrer">
      {body}
    </a>
  );
}

const MindMapSVG = forwardRef(function MindMapSVG({ data }, ref) {
  if (!data) return null;

  const { root, branches, isFocused, focusSkill } = data;
  const layout = calcLayout(branches);
  const { branches: laidOut, totalH, totalW, rootY, nodeX } = layout;
  const svgH = totalH + 16;

  return (
    <svg
      ref={ref}
      viewBox={`0 0 ${totalW} ${svgH}`}
      preserveAspectRatio="xMidYMin meet"
      role="img"
      aria-label="岗位技能思维导图"
      style={{ display: 'block', width: '100%', height: 'auto', background: MAP_BG }}
    >
      <defs>
        <filter id="shadow" x="-10%" y="-5%" width="130%" height="130%">
          <feDropShadow dx="0" dy="1" stdDeviation="2" floodOpacity="0.08" />
        </filter>
      </defs>

      <rect x={MARGIN} y={rootY} width={ROOT_W} height={ROOT_H} rx="12" fill="#1e293b" filter="url(#shadow)" />
      {root.includes('\n') ? (
        <>
          <text x={MARGIN + ROOT_W / 2} y={rootY + 22} textAnchor="middle" fill="#fff" fontSize="15" fontWeight="700">热门岗位</text>
          <text x={MARGIN + ROOT_W / 2} y={rootY + 42} textAnchor="middle" fill="#e2e8f0" fontSize="13" fontWeight="600">技能图谱</text>
        </>
      ) : (
        <text x={MARGIN + ROOT_W / 2} y={rootY + ROOT_H / 2} textAnchor="middle" dominantBaseline="central" fill="#fff" fontSize="14" fontWeight="700">{root}</text>
      )}

      {laidOut.map((b, bi) => {
        const fromX = MARGIN + ROOT_W;
        const fromY = rootY + ROOT_H / 2;
        const toY = b.nodeY + NODE_H / 2;
        const c = b.color;
        const midX = (fromX + nodeX) / 2;
        const path = `M ${fromX} ${fromY} C ${midX} ${fromY}, ${midX} ${toY}, ${nodeX} ${toY}`;

        return (
          <g key={bi}>
            <path d={path} fill="none" stroke={c.line} strokeWidth="2.5" opacity="0.7" />

            <rect x={nodeX} y={b.nodeY} width={NODE_W} height={NODE_H} rx="8" fill={c.bg} stroke={c.line} strokeWidth="2" filter="url(#shadow)" />
            <text x={nodeX + 12} y={b.nodeY + 22} fill={c.text} fontSize="13" fontWeight="700">
              {(b.job.jobTitle || '').length > 18 ? `${b.job.jobTitle.slice(0, 17)}…` : b.job.jobTitle}
            </text>
            <text x={nodeX + 12} y={b.nodeY + 40} fill={c.text} fontSize="12" opacity="0.75">{b.job.salary || '薪资面议'}</text>

            {b.skills.map((sk, si) => {
              const skillX = nodeX + NODE_W + GAP_X;
              let yOffset = NODE_H + 8;
              for (let pi = 0; pi < si; pi++) {
                const prev = b.skills[pi];
                const rows = Math.max(1, prev.tips ? prev.tips.length : 0);
                yOffset += Math.max(SKILL_H + 8, rows * (TIP_H + 4) + 4);
              }
              const skillY = b.nodeY + yOffset;
              const isHL = isFocused && focusSkill && sk.name.toLowerCase() === focusSkill.toLowerCase();
              const skillRows = Math.max(1, sk.tips ? sk.tips.length : 0);
              const skillBlockH = Math.max(SKILL_H + 8, skillRows * (TIP_H + 4) + 4);

              return (
                <g key={si}>
                  <line x1={nodeX + NODE_W} y1={b.nodeY + NODE_H / 2} x2={nodeX + NODE_W + GAP_X / 2} y2={b.nodeY + NODE_H / 2} stroke={c.line} strokeWidth="1.2" opacity="0.5" />
                  <line x1={nodeX + NODE_W + GAP_X / 2} y1={b.nodeY + NODE_H / 2} x2={nodeX + NODE_W + GAP_X / 2} y2={skillY + skillBlockH / 2} stroke={c.line} strokeWidth="1.2" opacity="0.5" />
                  <line x1={nodeX + NODE_W + GAP_X / 2} y1={skillY + skillBlockH / 2} x2={skillX} y2={skillY + skillBlockH / 2} stroke={c.line} strokeWidth="1.2" opacity="0.5" />

                  <rect x={skillX} y={skillY + (skillBlockH - SKILL_H) / 2} width={SKILL_W} height={SKILL_H} rx="5" fill={isHL ? '#fff7e6' : '#fff'} stroke={isHL ? '#fa8c16' : c.line} strokeWidth={isHL ? 2.5 : 1.5} />
                  <text x={skillX + SKILL_W / 2} y={skillY + (skillBlockH - SKILL_H) / 2 + SKILL_H / 2} textAnchor="middle" dominantBaseline="central" fill={isHL ? '#fa8c16' : c.text} fontSize="11" fontWeight={isHL ? '700' : '500'}>{sk.name}</text>

                  {sk.tips && sk.tips.map((tip, ti) => (
                    <TipNode
                      key={ti}
                      tip={tip}
                      tipX={skillX + SKILL_W + GAP_X}
                      tipY={skillY + ti * (TIP_H + 3)}
                      skillX={skillX}
                      skillY={skillY}
                      skillBlockH={skillBlockH}
                    />
                  ))}
                </g>
              );
            })}
          </g>
        );
      })}
    </svg>
  );
});

function buildMapData(jobs, focusSkill) {
  if (!jobs?.length) return null;

  const isFocused = Boolean(focusSkill);
  const filtered = isFocused
    ? jobs.filter((j) => (j.skills || '').toLowerCase().includes(focusSkill.toLowerCase()))
    : jobs;

  if (filtered.length === 0) return null;

  const branches = filtered.map((job, i) => {
    const rawSkills = String(job.skills || '')
      .split(/[,，、]/)
      .map((s) => s.trim())
      .filter(Boolean)
      .slice(0, 7);

    const skills = rawSkills.map((s) => ({
      name: s,
      tips: getLearningTips(s),
    }));

    return { job, skills, color: COLORS[i % COLORS.length] };
  });

  return {
    root: isFocused ? focusSkill : '热门岗位\n技能图谱',
    isFocused,
    focusSkill,
    branches,
  };
}

function JobSkillMindMap({ jobs, visible, onClose, focusSkill }) {
  const data = useMemo(() => buildMapData(jobs, focusSkill), [jobs, focusSkill]);
  const isFocused = Boolean(focusSkill);
  const svgRef = useRef(null);

  const handleSaveImage = async () => {
    if (!data || !svgRef.current) {
      message.error('暂无可保存的思维导图');
      return;
    }
    try {
      await downloadSvgAsPng(svgRef.current, buildSaveFilename(focusSkill));
    } catch {
      message.error('保存图片失败，请稍后重试');
    }
  };

  return (
    <Modal
      title={isFocused ? `技能思维导图 — "${focusSkill}"` : '岗位技能思维导图'}
      open={visible}
      onCancel={onClose}
      footer={(
        <Button type="primary" disabled={!data} onClick={handleSaveImage}>
          保存图片
        </Button>
      )}
      width="96vw"
      style={{ top: 6 }}
      styles={{ body: { padding: 0, maxHeight: 'calc(96vh - 90px)', overflow: 'auto' } }}
      destroyOnHidden
      centered
    >
      {!data ? (
        <div style={{ textAlign: 'center', padding: 80, color: '#94a3b8', fontSize: 15 }}>
          {jobs?.length ? `没有找到包含 "${focusSkill}" 技能的岗位` : '暂无岗位数据'}
        </div>
      ) : (
        <MindMapSVG ref={svgRef} data={data} />
      )}
    </Modal>
  );
}

export default JobSkillMindMap;
