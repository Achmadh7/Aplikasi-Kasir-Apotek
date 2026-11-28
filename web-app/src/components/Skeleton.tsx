import React from 'react';

const CARD = 'bg-[#E8F2EC] rounded-2xl border border-[#B7D6C3]';

const Bone: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`skeleton-shimmer rounded-xl ${className}`} />
);

export const PageSkeleton: React.FC<{ tab?: string }> = ({ tab = 'beranda' }) => {
  const isPos = tab === 'kasir';
  const isList = ['stok', 'riwayat', 'pbf', 'keuangan', 'pengingat'].includes(tab);
  const isPanel = tab === 'backup' || tab === 'authority';

  return (
    <div
      className="space-y-4"
      role="status"
      aria-busy="true"
      aria-label="Memuat halaman..."
    >
      {/* Header row */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-2">
          <Bone className="h-6 w-44" />
          <Bone className="h-3.5 w-64" />
        </div>
        <Bone className="h-10 w-32 rounded-full" />
      </div>

      {isPos ? (
        <div className={`${CARD} p-4 space-y-4`}>
          <Bone className="h-11 w-full rounded-xl" />
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className="rounded-xl border border-[#B7D6C3]/70 p-3 space-y-2.5"
              >
                <Bone className="h-4 w-3/4" />
                <Bone className="h-3 w-1/2" />
                <div className="flex items-center justify-between pt-1">
                  <Bone className="h-4 w-20" />
                  <Bone className="h-7 w-7 rounded-lg" />
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : isList ? (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className={`${CARD} p-4 space-y-2.5`}>
                <Bone className="h-3 w-20" />
                <Bone className="h-6 w-28" />
              </div>
            ))}
          </div>
          <div className={`${CARD} p-4 space-y-3`}>
            <div className="flex flex-wrap gap-2.5">
              <Bone className="h-10 flex-1 min-w-[200px]" />
              <Bone className="h-10 w-32" />
            </div>
            <div className="space-y-2.5 pt-1">
              {Array.from({ length: 6 }).map((_, i) => (
                <Bone key={i} className="h-12 w-full" />
              ))}
            </div>
          </div>
        </>
      ) : isPanel ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className={`${CARD} p-5 space-y-3.5`}>
              <Bone className="h-5 w-40" />
              <Bone className="h-3.5 w-3/4" />
              <Bone className="h-11 w-full" />
              <Bone className="h-10 w-36 rounded-full" />
            </div>
          ))}
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className={`${CARD} p-4 space-y-3`}>
                <div className="flex items-center justify-between">
                  <Bone className="h-3.5 w-24" />
                  <Bone className="h-8 w-8 rounded-xl" />
                </div>
                <Bone className="h-7 w-32" />
                <Bone className="h-3 w-20" />
              </div>
            ))}
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
            <div className={`${CARD} lg:col-span-7 p-5 space-y-4`}>
              <Bone className="h-5 w-40" />
              <Bone className="h-52 w-full" />
            </div>
            <div className={`${CARD} lg:col-span-5 p-5 space-y-3`}>
              <Bone className="h-5 w-36" />
              {Array.from({ length: 4 }).map((_, i) => (
                <Bone key={i} className="h-11 w-full" />
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export const AppBootSkeleton: React.FC = () => (
  <div
    className="min-h-[100dvh] flex flex-col md:flex-row md:p-3 md:gap-3"
    role="status"
    aria-busy="true"
    aria-label="Memuat aplikasi..."
  >
    {/* Mobile Top Bar Skeleton */}
    <header className="md:hidden sticky top-0 z-30 bg-[#142E27] px-4 pb-3 pt-[max(0.75rem,env(safe-area-inset-top))] flex items-center justify-between border-b border-[#244C41]">
      <div className="flex items-center gap-2.5">
        <div className="skeleton-shimmer-dark h-8 w-16 rounded-full" />
        <div className="skeleton-shimmer-dark h-8 w-8 rounded-full" />
        <div className="space-y-1.5">
          <div className="skeleton-shimmer-dark h-3.5 w-24 rounded-md" />
          <div className="skeleton-shimmer-dark h-2.5 w-28 rounded-md" />
        </div>
      </div>
      <div className="skeleton-shimmer-dark h-8 w-24 rounded-full" />
    </header>

    {/* Desktop Floating Bun Sidebar Skeleton */}
    <aside className="hidden md:flex md:sticky md:top-3 md:h-[calc(100dvh-1.5rem)] md:w-64 rounded-[26px] bg-[#142E27] border border-[#244C41] p-4 flex-col shrink-0 shadow-[0_14px_30px_rgba(20,46,39,0.22)]">
      <div className="flex items-center gap-3 pb-4 mb-4 border-b border-[#244C41]">
        <div className="skeleton-shimmer-dark w-10 h-10 rounded-full shrink-0" />
        <div className="space-y-1.5 flex-1">
          <div className="skeleton-shimmer-dark h-4 w-28 rounded-md" />
          <div className="skeleton-shimmer-dark h-2.5 w-20 rounded-md" />
        </div>
      </div>
      <div className="flex flex-col gap-2">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="skeleton-shimmer-dark h-9 w-full rounded-full" />
        ))}
      </div>
    </aside>

    {/* Main Content Skeleton */}
    <main className="flex-1 min-w-0 p-3.5 sm:p-6 md:py-3 md:px-4 max-w-7xl w-full mx-auto">
      <div className="hidden md:flex items-center justify-end mb-3">
        <Bone className="h-9 w-36 rounded-full" />
      </div>
      <PageSkeleton tab="beranda" />
    </main>
  </div>
);
