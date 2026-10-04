/**
 * Soft fused SVG scenes for student home cards.
 * Meant as ambient layers (low contrast), not sticker images.
 */

function CampusScene() {
  return (
    <svg viewBox="0 0 320 220" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <circle cx="268" cy="36" r="54" fill="#3B82F6" opacity="0.18" />
      <circle cx="48" cy="188" r="38" fill="#93C5FD" opacity="0.35" />
      <rect x="210" y="150" width="42" height="42" rx="8" fill="#60A5FA" opacity="0.28" transform="rotate(18 231 171)" />
      <path d="M70 168V92h28v76H70Zm36 0V72h32v96h-32Zm40 0V104h26v64h-26Zm34 0V84h30v84h-30Z" fill="#3B82F6" opacity="0.22" />
      <path d="M62 168h166v8H62z" fill="#2563EB" opacity="0.2" />
      <path d="M118 72l22-18 22 18v12h-44V72Z" fill="#60A5FA" opacity="0.35" />
      <circle cx="140" cy="48" r="6" fill="#F59E0B" opacity="0.55" />
      <path d="M40 40c18-8 34-4 42 8" stroke="#3B82F6" strokeWidth="3" strokeLinecap="round" opacity="0.25" />
    </svg>
  );
}

function ScoreScene() {
  return (
    <svg viewBox="0 0 420 200" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <circle cx="360" cy="40" r="70" fill="#3B82F6" opacity="0.12" />
      <circle cx="48" cy="160" r="46" fill="#93C5FD" opacity="0.28" />
      <circle cx="120" cy="92" r="34" stroke="#F59E0B" strokeWidth="10" opacity="0.28" />
      <circle cx="210" cy="92" r="34" stroke="#3B82F6" strokeWidth="10" opacity="0.32" />
      <circle cx="300" cy="92" r="34" stroke="#10B981" strokeWidth="10" opacity="0.28" />
      <path d="M86 150h248" stroke="#93C5FD" strokeWidth="8" strokeLinecap="round" opacity="0.35" />
      <rect x="330" y="118" width="36" height="36" rx="8" fill="#60A5FA" opacity="0.3" transform="rotate(-12 348 136)" />
      <circle cx="70" cy="56" r="8" fill="#3B82F6" opacity="0.3" />
      <circle cx="390" cy="150" r="10" fill="#F59E0B" opacity="0.35" />
    </svg>
  );
}

function NoticeScene() {
  return (
    <svg viewBox="0 0 280 240" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <circle cx="220" cy="48" r="64" fill="#3B82F6" opacity="0.16" />
      <circle cx="40" cy="200" r="44" fill="#93C5FD" opacity="0.3" />
      <rect x="188" y="150" width="48" height="48" rx="10" fill="#60A5FA" opacity="0.28" transform="rotate(22 212 174)" />
      <path d="M92 78c28-18 62-10 74 14l18 36c4 8-2 18-12 18H98c-10 0-16-10-12-18l18-36c2-6 0-10-12-14Z" fill="#3B82F6" opacity="0.28" />
      <circle cx="148" cy="108" r="10" fill="#F59E0B" opacity="0.45" />
      <path d="M118 168h72v10H118zM126 184h56v8H126z" fill="#2563EB" opacity="0.22" />
      <circle cx="64" cy="72" r="7" fill="#60A5FA" opacity="0.4" />
      <circle cx="236" cy="108" r="5" fill="#3B82F6" opacity="0.35" />
      <path d="M48 120c20 0 28 16 44 16" stroke="#93C5FD" strokeWidth="4" strokeLinecap="round" opacity="0.5" />
    </svg>
  );
}

const SCENES = {
  campus: CampusScene,
  scores: ScoreScene,
  notice: NoticeScene
};

function HomeFusedArt({ variant = 'notice', className = '' }) {
  const Scene = SCENES[variant] || NoticeScene;
  return (
    <div className={`home-fused-art home-fused-art-${variant} ${className}`.trim()} aria-hidden="true">
      <Scene />
    </div>
  );
}

export default HomeFusedArt;
