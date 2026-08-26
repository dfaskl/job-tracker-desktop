(() => {
  const BASE_WIDTH = 1920;
  const BASE_HEIGHT = 1080;
  const MIN_SCALE = 1;
  const MAX_SCALE = 1.5;
  let resizeFrame = 0;

  function availableSize() {
    const viewport = window.visualViewport;
    const pixelRatio = Math.max(1, window.devicePixelRatio || 1);
    const cssWidth = Math.min(screen.availWidth || window.innerWidth, viewport?.width || window.innerWidth);
    const cssHeight = Math.min(screen.availHeight || window.innerHeight, viewport?.height || window.innerHeight);
    return {
      width: cssWidth * pixelRatio,
      height: cssHeight * pixelRatio
    };
  }

  function preferredScale() {
    const { width, height } = availableSize();
    const proportionalScale = Math.min(width / BASE_WIDTH, height / BASE_HEIGHT);
    return Math.min(MAX_SCALE, Math.max(MIN_SCALE, proportionalScale));
  }

  function applyAdaptiveScale() {
    const scale = Math.round(preferredScale() * 100) / 100;
    document.documentElement.style.zoom = String(scale);
    document.documentElement.dataset.uiScale = String(scale);
    document.documentElement.style.setProperty('--ui-scale', String(scale));
  }

  function scheduleAdaptiveScale() {
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(applyAdaptiveScale);
  }

  applyAdaptiveScale();
  window.addEventListener('resize', scheduleAdaptiveScale, { passive: true });
  window.visualViewport?.addEventListener('resize', scheduleAdaptiveScale, { passive: true });
  screen.orientation?.addEventListener('change', scheduleAdaptiveScale);
})();