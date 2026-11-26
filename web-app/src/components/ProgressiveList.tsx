import React, { useCallback, useEffect, useRef, useState } from 'react';
import { nextProgressiveLimit } from '../types';

export function useIsMdUp(): boolean {
  const [isMd, setIsMd] = useState(() =>
    typeof window !== 'undefined' ? window.matchMedia('(min-width: 768px)').matches : true
  );

  useEffect(() => {
    const mq = window.matchMedia('(min-width: 768px)');
    const onChange = () => setIsMd(mq.matches);
    onChange();
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);

  return isMd;
}

interface ProgressiveOptions {
  initial?: number;
  step?: number;
  resetKey?: string;
  scrollContainerRef?: React.RefObject<HTMLElement>;
}

/**
 * Incrementally slices large arrays so initial render, filtering, and scrolling
 * stay responsive, automatically expanding when the user scrolls near the end.
 */
export function useProgressiveList<T>(
  items: readonly T[],
  { initial = 60, step = 60, resetKey = '', scrollContainerRef }: ProgressiveOptions = {}
) {
  const [limit, setLimit] = useState(initial);
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const total = items.length;
  const shownCount = Math.min(limit, total);
  const hasMore = shownCount < total;

  useEffect(() => {
    setLimit(initial);
    const el = scrollContainerRef?.current;
    if (el) el.scrollTop = 0;
  }, [resetKey, initial, scrollContainerRef]);

  const loadMore = useCallback(() => {
    setLimit((cur) => {
      const next = nextProgressiveLimit(cur, step, total);
      return next === cur ? cur : next;
    });
  }, [step, total]);

  useEffect(() => {
    if (!hasMore) return;
    const container = scrollContainerRef?.current;
    const sentinel = sentinelRef.current;

    const maybeFillContainer = () => {
      if (!container || container.scrollHeight <= container.clientHeight + 4) return;
      if (container.scrollHeight - container.scrollTop - container.clientHeight <= 280) {
        loadMore();
      }
    };

    if (container) {
      container.addEventListener('scroll', maybeFillContainer, { passive: true });
      maybeFillContainer();
    }

    if (sentinel && typeof IntersectionObserver !== 'undefined') {
      const observer = new IntersectionObserver(
        (entries) => {
          if (entries.some((e) => e.isIntersecting)) loadMore();
        },
        { rootMargin: '320px' }
      );
      observer.observe(sentinel);
      return () => {
        observer.disconnect();
        container?.removeEventListener('scroll', maybeFillContainer);
      };
    }

    return () => {
      container?.removeEventListener('scroll', maybeFillContainer);
    };
  }, [hasMore, limit, loadMore, scrollContainerRef]);

  return {
    visibleItems: items.slice(0, shownCount),
    shownCount,
    total,
    remaining: Math.max(0, total - shownCount),
    hasMore,
    loadMore,
    sentinelRef,
    step,
  };
}

export const ListPaginationFooter: React.FC<{
  shown: number;
  total: number;
  remaining: number;
  step: number;
  itemLabel?: string;
  onLoadMore: () => void;
  sentinelRef: React.RefObject<HTMLDivElement>;
  className?: string;
}> = ({
  shown,
  total,
  remaining,
  step,
  itemLabel = 'data',
  onLoadMore,
  sentinelRef,
  className = '',
}) => {
  if (total === 0) return null;
  if (remaining <= 0 && total <= step) return null;
  const nextCount = Math.min(step, remaining);

  return (
    <div
      ref={sentinelRef}
      className={`px-4 py-3 flex flex-wrap items-center justify-between gap-2 text-xs text-[#57534E] ${className}`}
    >
      <span>
        Menampilkan <strong className="text-[#1C1917]">{shown.toLocaleString('id-ID')}</strong> dari{' '}
        <strong className="text-[#1C1917]">{total.toLocaleString('id-ID')}</strong> {itemLabel}
      </span>
      {remaining > 0 && (
        <button
          type="button"
          onClick={onLoadMore}
          className="min-h-[36px] px-3 py-1.5 rounded-xl bg-[#DCEBE2] hover:bg-[#D0E3D8] border border-[#B7D6C3] text-xs font-bold text-[#1C1917] transition"
        >
          Muat {nextCount} lagi (sisa {remaining.toLocaleString('id-ID')})
        </button>
      )}
    </div>
  );
};
