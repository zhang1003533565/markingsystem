import { useEffect, useRef } from 'react';
import * as echarts from 'echarts';

/**
 * 轻量 ECharts 封装。
 * - visible=false 时（父容器 display:none）释放实例与监听，避免隐藏图表持续占用 canvas/resize。
 *   恢复可见后按当前尺寸重新 init。
 * - option 引用稳定时不会重复 setOption；option 变化仅在可见状态下重绘。
 */
function EChartPanel({ option, height = 320, visible = true }) {
  const chartRef = useRef(null);
  const instanceRef = useRef(null);
  const resizeObserverRef = useRef(null);
  const handleResizeRef = useRef(() => {});

  const disposeChart = () => {
    resizeObserverRef.current?.disconnect();
    resizeObserverRef.current = null;
    window.removeEventListener('resize', handleResizeRef.current);
    handleResizeRef.current = () => {};
    instanceRef.current?.dispose();
    instanceRef.current = null;
  };

  // 初始化 / 销毁受可见性控制
  useEffect(() => {
    if (!visible) {
      disposeChart();
      return undefined;
    }
    if (!chartRef.current) return undefined;

    if (!instanceRef.current) {
      instanceRef.current = echarts.init(chartRef.current);
    }
    instanceRef.current.setOption(option, true);

    // 容器尺寸变化时重测（不仅依赖 window resize），隐藏→显示纠正尺寸
    const handleResize = () => instanceRef.current?.resize();
    handleResizeRef.current = handleResize;
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserverRef.current = new ResizeObserver(handleResize);
      resizeObserverRef.current.observe(chartRef.current);
    }
    window.addEventListener('resize', handleResize);

    return disposeChart;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  // option 变化：仅当可见时同步到实例
  useEffect(() => {
    if (!visible) return undefined;
    if (!instanceRef.current) return undefined;
    instanceRef.current.setOption(option, true);
  }, [option, visible]);

  return <div ref={chartRef} style={{ width: '100%', height }} />;
}

export default EChartPanel;
