import React, { useEffect, useRef, useState } from 'react';

interface AnimatedNumberProps {
  value: number;
  format?: (val: number) => string;
  duration?: number;
  className?: string;
}

/**
 * Animates numeric values smoothly from the previous value to the target
 * using an Ease-Out Cubic curve with tabular-nums to prevent layout jitter.
 */
export const AnimatedNumber: React.FC<AnimatedNumberProps> = ({
  value,
  format = (v) => String(Math.round(v)),
  duration = 900,
  className = '',
}) => {
  const [display, setDisplay] = useState<number>(0);
  const currentRef = useRef<number>(0);

  useEffect(() => {
    const prefersReduced =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    if (prefersReduced || duration <= 0) {
      currentRef.current = value;
      setDisplay(value);
      return;
    }

    const from = currentRef.current;
    const to = value;
    if (from === to) {
      setDisplay(to);
      return;
    }

    let rafId = 0;
    const start = performance.now();

    const step = (now: number) => {
      const elapsed = now - start;
      const t = Math.min(1, elapsed / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      const next = Math.round(from + (to - from) * eased);
      currentRef.current = next;
      setDisplay(next);
      if (t < 1) {
        rafId = requestAnimationFrame(step);
      } else {
        currentRef.current = to;
        setDisplay(to);
      }
    };

    rafId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(rafId);
  }, [value, duration]);

  return <span className={`tabular-nums ${className}`}>{format(display)}</span>;
};
