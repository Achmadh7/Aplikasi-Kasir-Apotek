import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2, Info, RefreshCw, X, XCircle } from 'lucide-react';

export type ToastType = 'info' | 'success' | 'warning' | 'error' | 'loading';

export interface NotifyOptions {
  key?: string;
  detail?: string;
  duration?: number | null;
}

interface ToastItem {
  id: string;
  message: string;
  detail?: string;
  type: ToastType;
}

interface ConfirmState {
  title: string;
  message: string;
  danger?: boolean;
  confirmLabel?: string;
  onConfirm: () => void;
}

interface FeedbackContextValue {
  notify: (message: string, type?: ToastType, options?: NotifyOptions) => void;
  dismissByKey: (key: string) => void;
  askConfirm: (
    message: string,
    onConfirm: () => void,
    options?: { title?: string; danger?: boolean; confirmLabel?: string }
  ) => void;
}

const FeedbackContext = createContext<FeedbackContextValue | null>(null);

const TOAST_TONES: Record<ToastType, { border: string; iconWrap: string }> = {
  success: {
    border: 'border-[#B7D6C3]',
    iconWrap: 'bg-[#DCFCE7] border-[#BBF7D0] text-[#166534]',
  },
  loading: {
    border: 'border-[#7DD3FC]',
    iconWrap: 'bg-[#E0F2FE] border-[#BAE6FD] text-[#075985]',
  },
  warning: {
    border: 'border-[#FCD34D]',
    iconWrap: 'bg-[#FEF3C7] border-[#FDE68A] text-[#92400E]',
  },
  error: {
    border: 'border-[#FCA5A5]',
    iconWrap: 'bg-[#FEE2E2] border-[#FECACA] text-[#991B1B]',
  },
  info: {
    border: 'border-[#B7D6C3]',
    iconWrap: 'bg-[#DCEBE2] border-[#B7D6C3] text-[#142E27]',
  },
};

export const FeedbackProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [confirmState, setConfirmState] = useState<ConfirmState | null>(null);
  const timersRef = useRef<Map<string, number>>(new Map());
  const seqRef = useRef(0);

  const clearTimer = useCallback((id: string) => {
    const existing = timersRef.current.get(id);
    if (existing !== undefined) {
      window.clearTimeout(existing);
      timersRef.current.delete(id);
    }
  }, []);

  const dismissToast = useCallback(
    (id: string) => {
      clearTimer(id);
      setToasts((prev) => prev.filter((t) => t.id !== id));
    },
    [clearTimer]
  );

  const notify = useCallback(
    (message: string, type: ToastType = 'info', options?: NotifyOptions) => {
      const id = options?.key || `toast-${++seqRef.current}`;
      clearTimer(id);
      setToasts((prev) => {
        const nextItem: ToastItem = { id, message, detail: options?.detail, type };
        const filtered = prev.filter((t) => t.id !== id);
        const combined = [...filtered, nextItem];
        if (combined.length > 3) {
          for (const dropped of combined.slice(0, combined.length - 3)) {
            clearTimer(dropped.id);
          }
          return combined.slice(-3);
        }
        return combined;
      });
      const duration = options?.duration === undefined ? 3500 : options.duration;
      if (duration !== null && duration > 0) {
        const timer = window.setTimeout(() => {
          timersRef.current.delete(id);
          setToasts((prev) => prev.filter((t) => t.id !== id));
        }, duration);
        timersRef.current.set(id, timer);
      }
    },
    [clearTimer]
  );

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      timers.forEach((t) => window.clearTimeout(t));
      timers.clear();
    };
  }, []);

  const askConfirm = useCallback(
    (
      message: string,
      onConfirm: () => void,
      options?: { title?: string; danger?: boolean; confirmLabel?: string }
    ) => {
      setConfirmState({
        title: options?.title || 'Konfirmasi',
        message,
        danger: options?.danger,
        confirmLabel: options?.confirmLabel || 'Ya, Lanjutkan',
        onConfirm,
      });
    },
    []
  );

  useEffect(() => {
    if (!confirmState) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setConfirmState(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [confirmState]);

  return (
    <FeedbackContext.Provider value={{ notify, dismissByKey: dismissToast, askConfirm }}>
      {children}

      {/* Unified Warm Paper Card Notifications (Opsi B) */}
      <div className="no-print fixed bottom-[calc(4.75rem+env(safe-area-inset-bottom))] lg:bottom-5 left-3 right-3 sm:left-auto sm:right-5 sm:w-[356px] max-w-full z-50 flex flex-col gap-2 pointer-events-none">
        {toasts.map((t) => {
          const tone = TOAST_TONES[t.type];
          const Icon =
            t.type === 'error'
              ? XCircle
              : t.type === 'warning'
              ? AlertTriangle
              : t.type === 'success'
              ? CheckCircle2
              : t.type === 'loading'
              ? RefreshCw
              : Info;
          return (
            <div
              key={t.id}
              role={t.type === 'error' ? 'alert' : 'status'}
              aria-live={t.type === 'error' ? 'assertive' : 'polite'}
              aria-atomic="true"
              className={`pill-pop pointer-events-auto bg-[#FAF8F4] text-[#1C1917] border ${tone.border} rounded-2xl shadow-[0_12px_28px_rgba(20,46,39,0.14)] px-3.5 py-2.5 flex items-center gap-3`}
            >
              <span
                className={`w-8 h-8 rounded-xl border flex items-center justify-center shrink-0 ${tone.iconWrap}`}
                aria-hidden="true"
              >
                <Icon className={`w-4 h-4 ${t.type === 'loading' ? 'animate-spin' : ''}`} />
              </span>
              <div className="flex-1 min-w-0">
                <p className="text-[13px] font-bold leading-snug text-[#1C1917] break-words">
                  {t.message}
                </p>
                {t.detail && (
                  <p className="text-xs text-[#57534E] leading-snug mt-0.5 break-words">
                    {t.detail}
                  </p>
                )}
              </div>
              <button
                type="button"
                onClick={() => dismissToast(t.id)}
                className="min-w-[44px] min-h-[44px] -my-1.5 -mr-1.5 flex items-center justify-center rounded-xl text-[#57534E] hover:text-[#1C1917] hover:bg-[#DCEBE2]/70 transition shrink-0"
                aria-label="Tutup notifikasi"
              >
                <X className="w-4 h-4" aria-hidden="true" />
              </button>
            </div>
          );
        })}
      </div>

      {/* Confirm Modal */}
      {confirmState && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirm-modal-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setConfirmState(null);
          }}
          className="no-print fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4"
        >
          <div className="bg-[#F6F4EF] border border-[#B7D6C3] rounded-2xl shadow-xl max-w-sm w-full p-5 space-y-4">
            <div className="flex items-start gap-3">
              <div
                className={`p-2 rounded-xl shrink-0 ${
                  confirmState.danger
                    ? 'bg-red-100 text-[#BA1A1A]'
                    : 'bg-[#DCFCE7] text-[#166534]'
                }`}
              >
                <AlertTriangle className="w-5 h-5" aria-hidden="true" />
              </div>
              <div>
                <h3 id="confirm-modal-title" className="font-bold text-[#1C1917] text-base">
                  {confirmState.title}
                </h3>
                <p className="text-sm text-[#57534E] mt-1">{confirmState.message}</p>
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setConfirmState(null)}
                className="min-h-[44px] px-4 py-2 rounded-xl text-sm font-medium text-[#57534E] hover:bg-black/5"
              >
                Batal
              </button>
              <button
                type="button"
                onClick={() => {
                  const fn = confirmState.onConfirm;
                  setConfirmState(null);
                  fn();
                }}
                className={`min-h-[44px] px-4 py-2 rounded-xl text-sm font-semibold text-white ${
                  confirmState.danger
                    ? 'bg-[#BA1A1A] hover:bg-[#991B1B]'
                    : 'bg-[#15803D] hover:bg-[#166534]'
                }`}
              >
                {confirmState.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}
    </FeedbackContext.Provider>
  );
};

export function useFeedback() {
  const ctx = useContext(FeedbackContext);
  if (!ctx) throw new Error('useFeedback must be used within FeedbackProvider');
  return ctx;
}
