import React, { useEffect, useState } from 'react';
import { ShieldCheck, Plus, Trash2, Lock, KeyRound } from 'lucide-react';
import { useStore } from '../store';
import { useFeedback } from './Feedback';
import {
  AuthorityPinConfig,
  fetchAuthorityPin,
  normalizeEmail,
  saveAuthorityPin,
  verifyAuthorityPin,
} from '../firebase';

const UNLOCK_TTL_MS = 5 * 60 * 1000;
const MAX_FAILED_ATTEMPTS = 5;
const COOLDOWN_MS = 30 * 1000;
const cleanDigits = (value: string) => value.replace(/\D/g, '').slice(0, 6);

export const AuthorityScreen: React.FC = () => {
  const { user, authorityEmails, addAuthorityEmail, removeAuthorityEmail } = useStore();
  const { notify } = useFeedback();
  const [newEmail, setNewEmail] = useState('');
  const [saving, setSaving] = useState(false);

  const [pinConfig, setPinConfig] = useState<AuthorityPinConfig | null>(null);
  const [loadingPin, setLoadingPin] = useState(true);
  const [pinError, setPinError] = useState(false);
  const [unlocked, setUnlocked] = useState(false);
  const [verifying, setVerifying] = useState(false);

  const [pinInput, setPinInput] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [failedAttempts, setFailedAttempts] = useState(0);
  const [lockedUntil, setLockedUntil] = useState(0);
  const [cooldownSec, setCooldownSec] = useState(0);

  const [showChangePin, setShowChangePin] = useState(false);
  const [newPin, setNewPin] = useState('');
  const [confirmNewPin, setConfirmNewPin] = useState('');

  const currentEmail = normalizeEmail(user?.email);

  useEffect(() => {
    let active = true;
    fetchAuthorityPin()
      .then((cfg) => {
        if (!active) return;
        setPinConfig(cfg);
        setPinError(false);
      })
      .catch(() => {
        if (!active) return;
        setPinError(true);
        notify('Gagal memuat konfigurasi PIN Authority', 'error');
      })
      .finally(() => {
        if (active) setLoadingPin(false);
      });
    return () => {
      active = false;
    };
  }, [notify]);

  useEffect(() => {
    if (!unlocked) return;
    const timer = window.setTimeout(() => {
      setUnlocked(false);
      setShowChangePin(false);
      notify('Menu Authority dikunci otomatis', 'info');
    }, UNLOCK_TTL_MS);
    return () => window.clearTimeout(timer);
  }, [unlocked, notify]);

  useEffect(() => {
    if (!lockedUntil) return;
    const update = () => {
      const rem = Math.ceil((lockedUntil - Date.now()) / 1000);
      if (rem <= 0) {
        setLockedUntil(0);
        setCooldownSec(0);
        setFailedAttempts(0);
      } else {
        setCooldownSec(rem);
      }
    };
    update();
    const id = window.setInterval(update, 1000);
    return () => window.clearInterval(id);
  }, [lockedUntil]);

  const handleSetupPin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pinInput.length < 4) {
      notify('PIN minimal 4 digit angka', 'error');
      return;
    }
    if (pinInput !== confirmPin) {
      notify('Konfirmasi PIN tidak cocok', 'error');
      return;
    }
    setVerifying(true);
    try {
      const saved = await saveAuthorityPin(pinInput);
      setPinConfig(saved);
      setPinInput('');
      setConfirmPin('');
      setUnlocked(true);
      notify('PIN Authority berhasil dibuat', 'success');
    } catch (err: any) {
      notify(err?.message || 'Gagal menyimpan PIN', 'error');
    } finally {
      setVerifying(false);
    }
  };

  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pinConfig || cooldownSec > 0) return;
    if (pinInput.length < 4) {
      notify('Masukkan PIN 4–6 digit', 'error');
      return;
    }
    setVerifying(true);
    try {
      const ok = await verifyAuthorityPin(pinInput, pinConfig);
      if (ok) {
        setPinInput('');
        setFailedAttempts(0);
        setUnlocked(true);
        notify('Menu Authority terbuka selama 5 menit', 'success');
      } else {
        const nextFails = failedAttempts + 1;
        setPinInput('');
        if (nextFails >= MAX_FAILED_ATTEMPTS) {
          setLockedUntil(Date.now() + COOLDOWN_MS);
          setFailedAttempts(nextFails);
          notify('Terlalu banyak percobaan salah. Tunggu 30 detik.', 'error');
        } else {
          setFailedAttempts(nextFails);
          notify(`PIN salah (${nextFails}/${MAX_FAILED_ATTEMPTS})`, 'error');
        }
      }
    } catch (err: any) {
      notify(err?.message || 'Verifikasi PIN gagal', 'error');
    } finally {
      setVerifying(false);
    }
  };

  const handleChangePin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPin.length < 4) {
      notify('PIN baru minimal 4 digit angka', 'error');
      return;
    }
    if (newPin !== confirmNewPin) {
      notify('Konfirmasi PIN baru tidak cocok', 'error');
      return;
    }
    setSaving(true);
    try {
      const saved = await saveAuthorityPin(newPin);
      setPinConfig(saved);
      setNewPin('');
      setConfirmNewPin('');
      setShowChangePin(false);
      notify('PIN Authority berhasil diperbarui', 'success');
    } catch (err: any) {
      notify(err?.message || 'Gagal mengubah PIN', 'error');
    } finally {
      setSaving(false);
    }
  };

  if (loadingPin) {
    return (
      <div className="max-w-sm mx-auto mt-6">
        <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-6 text-center text-xs font-semibold text-[#78716C] dark:text-[#93B5A9]">
          Memuat kunci Authority...
        </div>
      </div>
    );
  }

  if (pinError) {
    return (
      <div className="max-w-sm mx-auto mt-6">
        <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-6 text-center space-y-3">
          <p className="text-sm font-bold text-[#1C1917] dark:text-white">
            Tidak dapat memuat kunci Authority
          </p>
          <p className="text-xs text-[#78716C] dark:text-[#93B5A9]">
            Periksa koneksi internet lalu coba lagi.
          </p>
          <button
            type="button"
            onClick={() => {
              setLoadingPin(true);
              setPinError(false);
              fetchAuthorityPin()
                .then((cfg) => setPinConfig(cfg))
                .catch(() => setPinError(true))
                .finally(() => setLoadingPin(false));
            }}
            className="px-4 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold transition"
          >
            Coba Lagi
          </button>
        </div>
      </div>
    );
  }

  if (!unlocked) {
    const isSetup = !pinConfig;
    return (
      <div className="max-w-sm mx-auto mt-6">
        <form
          onSubmit={isSetup ? handleSetupPin : handleUnlock}
          className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-6 shadow-sm text-center space-y-4"
        >
          <div className="w-12 h-12 mx-auto rounded-2xl bg-[#DCFCE7] dark:bg-[#15803D]/25 text-[#15803D] dark:text-[#86EFAC] flex items-center justify-center">
            <Lock className="w-6 h-6" />
          </div>
          <div className="space-y-1">
            <h2 className="text-lg font-extrabold text-[#1C1917] dark:text-white">
              {isSetup ? 'Buat PIN Authority' : 'Authority Terkunci'}
            </h2>
            <p className="text-xs text-[#78716C] dark:text-[#93B5A9]">
              {isSetup
                ? 'Atur PIN 4–6 digit untuk mengunci akses menu Authority.'
                : 'Masukkan PIN Authority untuk melihat dan mengubah daftar email.'}
            </p>
          </div>

          <div className="space-y-2.5 text-left">
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              autoFocus
              maxLength={6}
              value={pinInput}
              onChange={(e) => setPinInput(cleanDigits(e.target.value))}
              placeholder={isSetup ? 'PIN baru (4–6 digit)' : 'Masukkan PIN'}
              disabled={verifying || cooldownSec > 0}
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 dark:border-white/15 bg-slate-50 dark:bg-[#0F241E] text-center text-lg font-bold tracking-[0.35em] text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-[#15803D] disabled:opacity-60"
            />
            {isSetup && (
              <input
                type="password"
                inputMode="numeric"
                autoComplete="off"
                maxLength={6}
                value={confirmPin}
                onChange={(e) => setConfirmPin(cleanDigits(e.target.value))}
                placeholder="Ulangi PIN"
                disabled={verifying}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 dark:border-white/15 bg-slate-50 dark:bg-[#0F241E] text-center text-lg font-bold tracking-[0.35em] text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-[#15803D] disabled:opacity-60"
              />
            )}
          </div>

          <button
            type="submit"
            disabled={verifying || cooldownSec > 0}
            className="w-full px-4 py-2.5 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-sm font-bold flex items-center justify-center gap-2 disabled:opacity-60 transition"
          >
            <KeyRound className="w-4 h-4" />
            {cooldownSec > 0
              ? `Tunggu ${cooldownSec} detik...`
              : verifying
                ? 'Memproses...'
                : isSetup
                  ? 'Simpan & Buka'
                  : 'Buka Kunci'}
          </button>
        </form>
      </div>
    );
  }

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newEmail.trim()) return;
    setSaving(true);
    try {
      await addAuthorityEmail(newEmail);
      setNewEmail('');
      notify('Email berhasil ditambahkan ke Authority', 'success');
    } catch (err: any) {
      notify(err?.message || 'Gagal menambah email Authority', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleRemove = async (email: string) => {
    setSaving(true);
    try {
      await removeAuthorityEmail(email);
      notify(`Email ${email} dihapus dari Authority`, 'success');
    } catch (err: any) {
      notify(err?.message || 'Gagal menghapus email Authority', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-5 max-w-2xl">
      <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-5 shadow-sm">
        <div className="flex items-start justify-between gap-3 mb-2">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-[#DCFCE7] dark:bg-[#15803D]/25 text-[#15803D] dark:text-[#86EFAC]">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-extrabold text-[#1C1917] dark:text-white">
                Authority Login
              </h2>
              <p className="text-xs text-[#78716C] dark:text-[#93B5A9]">
                Hanya email Google di kolom Authority di bawah ini yang bisa masuk. Akun lain otomatis terpental keluar.
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={() => {
                setShowChangePin((v) => !v);
                setNewPin('');
                setConfirmNewPin('');
              }}
              className="px-3 py-1.5 rounded-xl border border-[#B7D6C3] dark:border-[#244C41] text-xs font-bold text-[#1C1917] dark:text-[#86EFAC] hover:bg-black/5 dark:hover:bg-white/5 flex items-center gap-1.5 transition"
            >
              <KeyRound className="w-3.5 h-3.5" />
              Ubah PIN
            </button>
            <button
              type="button"
              onClick={() => {
                setUnlocked(false);
                setShowChangePin(false);
              }}
              className="px-3 py-1.5 rounded-xl border border-[#B7D6C3] dark:border-[#244C41] text-xs font-bold text-[#1C1917] dark:text-[#86EFAC] hover:bg-black/5 dark:hover:bg-white/5 flex items-center gap-1.5 transition"
            >
              <Lock className="w-3.5 h-3.5" />
              Kunci
            </button>
          </div>
        </div>

        {showChangePin && (
          <form
            onSubmit={handleChangePin}
            className="mt-3 p-3 rounded-xl bg-white/70 dark:bg-[#0F241E] border border-[#B7D6C3] dark:border-[#244C41] flex flex-col sm:flex-row gap-2"
          >
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              maxLength={6}
              value={newPin}
              onChange={(e) => setNewPin(cleanDigits(e.target.value))}
              placeholder="PIN baru (4–6 digit)"
              className="flex-1 px-3 py-2 rounded-xl border border-slate-300 dark:border-white/15 bg-slate-50 dark:bg-[#142E27] text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-[#15803D]"
            />
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              maxLength={6}
              value={confirmNewPin}
              onChange={(e) => setConfirmNewPin(cleanDigits(e.target.value))}
              placeholder="Ulangi PIN baru"
              className="flex-1 px-3 py-2 rounded-xl border border-slate-300 dark:border-white/15 bg-slate-50 dark:bg-[#142E27] text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-[#15803D]"
            />
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold disabled:opacity-60 transition shrink-0"
            >
              Simpan PIN
            </button>
          </form>
        )}

        <form onSubmit={handleAdd} className="mt-4 flex flex-col sm:flex-row gap-2">
          <input
            type="email"
            required
            value={newEmail}
            onChange={(e) => setNewEmail(e.target.value)}
            placeholder="Tambah email Google (contoh: kasir@gmail.com)"
            className="flex-1 px-3.5 py-2.5 rounded-xl border border-slate-300 dark:border-white/15 bg-slate-50 dark:bg-[#142E27] text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-[#15803D]"
          />
          <button
            type="submit"
            disabled={saving}
            className="px-4 py-2.5 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-sm font-bold flex items-center justify-center gap-1.5 disabled:opacity-60 transition"
          >
            <Plus className="w-4 h-4" />
            Tambah
          </button>
        </form>
      </div>

      <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#101F38] border border-[#B7D6C3] dark:border-white/10 overflow-hidden shadow-sm">
        <div className="px-5 py-3 border-b border-slate-200 dark:border-white/10 flex items-center justify-between">
          <span className="text-xs font-extrabold uppercase tracking-wider text-[#1C1917] dark:text-[#86EFAC]">
            Kolom Authority (Email Diizinkan)
          </span>
          <span className="text-xs font-bold text-[#1C1917] dark:text-white">
            {authorityEmails.length} akun
          </span>
        </div>

        <div className="divide-y divide-slate-200 dark:divide-white/10">
          {authorityEmails.map((email) => {
            const isCurrent = email === currentEmail;
            return (
              <div
                key={email}
                className="px-5 py-3.5 flex items-center justify-between gap-3"
              >
                <div className="min-w-0 flex items-center gap-2">
                  <span className="text-sm font-semibold text-[#1C1917] dark:text-white truncate">
                    {email}
                  </span>
                  {isCurrent && (
                    <span className="px-2 py-0.5 rounded-full bg-emerald-500/15 text-[#1C1917] dark:text-emerald-300 text-[10px] font-bold shrink-0">
                      Sedang Login
                    </span>
                  )}
                </div>
                <button
                  onClick={() => handleRemove(email)}
                  disabled={saving || authorityEmails.length <= 1}
                  title={
                    authorityEmails.length <= 1
                      ? 'Minimal harus tersisa 1 email'
                      : 'Hapus dari Authority'
                  }
                  className="p-2 rounded-xl text-red-500 hover:bg-red-500/10 disabled:opacity-35 transition shrink-0"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
