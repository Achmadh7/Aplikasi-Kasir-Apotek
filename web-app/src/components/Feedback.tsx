import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from 'lucide-react';

type ToastType = 'info' | 'success' | 'error';

interface ToastItem {
  id: number;
  message: string;
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
  notify: (message: string, type?: ToastType) => void;
  askConfirm: (
    message: string,
    onConfirm: () => void,
    options?: { title?: string; danger?: boolean; confirmLabel?: string }
  ) => void;
}

const FeedbackContext = createContext<FeedbackContextValue | null>(null);

export const FeedbackProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [confirmState, setConfirmState] = useState<ConfirmState | null>(null);

  const dismissToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const notify = useCallback((message: string, type: ToastType = 'info') => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, type }]);
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3500);
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
    <FeedbackContext.Provider value={{ notify, askConfirm }}>
      {children}

      {/* Toast Stack */}
      <div className="no-print fixed bottom-[calc(4.75rem+env(safe-area-inset-bottom))] md:bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm w-[calc(100vw-2rem)] pointer-events-none">
        {toasts.map((t) => {
          const styles =
            t.type === 'error'
              ? 'bg-red-600 text-white'
              : t.type === 'success'
              ? 'bg-emerald-600 text-white'
              : 'bg-slate-900 text-white';
          const Icon =
            t.type === 'error' ? XCircle : t.type === 'success' ? CheckCircle2 : Info;
          return (
            <div
              key={t.id}
              className={`${styles} pointer-events-auto rounded-xl shadow-lg px-4 py-3 flex items-start gap-2.5 text-sm animate-in fade-in slide-in-from-bottom-2`}
            >
              <Icon className="w-4 h-4 shrink-0 mt-0.5" />
              <span className="flex-1 leading-snug">{t.message}</span>
              <button
                type="button"
                onClick={() => dismissToast(t.id)}
                className="opacity-70 hover:opacity-100"
                aria-label="Tutup"
              >
                <X className="w-4 h-4" />
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
          <div className="bg-[#F6F4EF] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] rounded-2xl shadow-xl max-w-sm w-full p-5 space-y-4">
            <div className="flex items-start gap-3">
              <div
                className={`p-2 rounded-xl shrink-0 ${
                  confirmState.danger
                    ? 'bg-red-100 text-red-600 dark:bg-red-950/60 dark:text-red-400'
                    : 'bg-emerald-100 text-[#15803D] dark:bg-[#1E3A30] dark:text-[#6EE7B7]'
                }`}
              >
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <h3 id="confirm-modal-title" className="font-bold text-[#1C1917] dark:text-white text-base">
                  {confirmState.title}
                </h3>
                <p className="text-sm text-[#57534E] dark:text-[#A7C4B8] mt-1">{confirmState.message}</p>
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setConfirmState(null)}
                className="min-h-[44px] px-4 py-2 rounded-xl text-sm font-medium text-[#57534E] dark:text-[#A7C4B8] hover:bg-black/5 dark:hover:bg-white/10"
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
                    ? 'bg-[#DC2626] hover:bg-[#B91C1C]'
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
