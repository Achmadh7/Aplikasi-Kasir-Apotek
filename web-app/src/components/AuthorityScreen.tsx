import React, { useEffect, useState } from 'react';
import { ShieldCheck, Plus, Trash2, Lock, KeyRound } from 'lucide-react';
import { useStore } from '../store';
import { useFeedback } from './Feedback';
import {
  AuthorityPinConfig,
  AuthorityRole,
  fetchAuthorityPin,
  normalizeEmail,
  reauthenticateOwnerWithGoogle,
  saveAuthorityPin,
  verifyAuthorityPin,
} from '../firebase';

const UNLOCK_TTL_MS = 5 * 60 * 1000;
const MAX_FAILED_ATTEMPTS = 5;
const COOLDOWN_MS = 30 * 1000;
const PIN_LOCK_STORAGE_KEY = 'apotek_lestari_pin_lock_until';
const cleanDigits = (value: string) => value.replace(/\D/g, '').slice(0, 6);

function readPersistedLockUntil(): number {
  try {
    const raw = Number(sessionStorage.getItem(PIN_LOCK_STORAGE_KEY) || 0);
    return Number.isFinite(raw) && raw > Date.now() ? raw : 0;
  } catch {
    return 0;
  }
}

function writePersistedLockUntil(until: number) {
  try {
    if (until > Date.now()) {
      sessionStorage.setItem(PIN_LOCK_STORAGE_KEY, String(until));
    } else {
      sessionStorage.removeItem(PIN_LOCK_STORAGE_KEY);
    }
  } catch {
    // ignore storage errors
  }
}

export const AuthorityScreen: React.FC = () => {
  const {
    user,
    authorityEmails,
    ownerEmails,
    currentRole,
    addAuthorityEmail,
    setAuthorityRole,
    removeAuthorityEmail,
  } = useStore();
  const { notify } = useFeedback();
  const [newEmail, setNewEmail] = useState('');
  const [newRole, setNewRole] = useState<AuthorityRole>('kasir');
  const [saving, setSaving] = useState(false);

  const [pinConfig, setPinConfig] = useState<AuthorityPinConfig | null>(null);
  const [loadingPin, setLoadingPin] = useState(true);
  const [pinError, setPinError] = useState(false);
  const [unlocked, setUnlocked] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [showResetPin, setShowResetPin] = useState(false);

  const [pinInput, setPinInput] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [failedAttempts, setFailedAttempts] = useState(0);
  const [lockedUntil, setLockedUntil] = useState(() => readPersistedLockUntil());
  const [cooldownSec, setCooldownSec] = useState(() => {
    const rem = Math.ceil((readPersistedLockUntil() - Date.now()) / 1000);
    return rem > 0 ? rem : 0;
  });

  const [showChangePin, setShowChangePin] = useState(false);
  const [newPin, setNewPin] = useState('');
  const [confirmNewPin, setConfirmNewPin] = useState('');

  const currentEmail = normalizeEmail(user?.email);
  const isOwner = currentRole === 'owner';

  useEffect(() => {
    if (!isOwner) {
      setLoadingPin(false);
      setPinError(false);
      setPinConfig(null);
      return;
    }
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
  }, [isOwner, notify]);

  useEffect(() => {
    if (!unlocked || !isOwner) return;
    const timer = window.setTimeout(() => {
      setUnlocked(false);
      setShowChangePin(false);
      notify('Menu Authority dikunci otomatis', 'info');
    }, UNLOCK_TTL_MS);
    return () => window.clearTimeout(timer);
  }, [unlocked, isOwner, notify]);

  useEffect(() => {
    if (!lockedUntil) return;
    const update = () => {
      const rem = Math.ceil((lockedUntil - Date.now()) / 1000);
      if (rem <= 0) {
        setLockedUntil(0);
        writePersistedLockUntil(0);
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
    const isResetting = Boolean(pinConfig && showResetPin && isOwner);
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
      if (isResetting) {
        await reauthenticateOwnerWithGoogle();
      }
      const saved = await saveAuthorityPin(pinInput);
      setPinConfig(saved);
      setPinInput('');
      setConfirmPin('');
      setFailedAttempts(0);
      setLockedUntil(0);
      writePersistedLockUntil(0);
      setCooldownSec(0);
      setShowResetPin(false);
      setUnlocked(true);
      notify(isResetting ? 'PIN Authority berhasil direset' : 'PIN Authority berhasil dibuat', 'success');
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
      notify('Masukkan PIN 4 sampai 6 digit', 'error');
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
          const until = Date.now() + COOLDOWN_MS;
          setLockedUntil(until);
          writePersistedLockUntil(until);
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

  if (!isOwner) {
    return (
      <div className="max-w-sm mx-auto mt-6">
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-6 shadow-xs text-center space-y-3">
          <div className="w-12 h-12 mx-auto rounded-2xl bg-[#FEF3C7] text-[#92400E] flex items-center justify-center">
            <Lock className="w-6 h-6" aria-hidden="true" />
          </div>
          <h2 className="text-lg font-extrabold text-[#1C1917]">
            Khusus Owner
          </h2>
          <p className="text-xs text-[#57534E]">
            Menu Authority dan daftar email akses terkunci khusus untuk akun Owner.
          </p>
        </div>
      </div>
    );
  }

  if (loadingPin) {
    return (
      <div className="max-w-sm mx-auto mt-6">
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-6 text-center text-xs font-semibold text-[#57534E]">
          Memuat kunci Authority...
        </div>
      </div>
    );
  }

  if (pinError) {
    return (
      <div className="max-w-sm mx-auto mt-6">
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-6 text-center space-y-3">
          <p className="text-sm font-bold text-[#1C1917]">
            Tidak dapat memuat kunci Authority
          </p>
          <p className="text-xs text-[#57534E]">
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
            className="min-h-[44px] px-4 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold transition"
          >
            Coba Lagi
          </button>
        </div>
      </div>
    );
  }

  if (!unlocked) {
    const isSetup = !pinConfig;
    const isResetting = !isSetup && showResetPin && isOwner;
    const isSettingPin = isSetup || isResetting;
    const blocked = !isSettingPin && cooldownSec > 0;
    return (
      <div className="max-w-sm mx-auto mt-6">
        <form
          onSubmit={isSettingPin ? handleSetupPin : handleUnlock}
          className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-6 shadow-xs text-center space-y-4"
        >
          <div className="w-12 h-12 mx-auto rounded-2xl bg-[#DCFCE7] text-[#166534] flex items-center justify-center">
            <Lock className="w-6 h-6" aria-hidden="true" />
          </div>
          <div className="space-y-1">
            <h2 className="text-lg font-extrabold text-[#1C1917]">
              {isSetup
                ? 'Buat PIN Authority'
                : isResetting
                  ? 'Reset PIN (Owner)'
                  : 'Authority Terkunci'}
            </h2>
            <p className="text-xs text-[#57534E]">
              {isSetup
                ? 'Atur PIN 4 sampai 6 digit untuk mengunci akses menu Authority.'
                : isResetting
                  ? 'Masukkan PIN baru 4 sampai 6 digit. Anda akan diminta verifikasi ulang akun Google Owner.'
                  : 'Masukkan PIN Authority untuk melihat dan mengubah daftar email.'}
            </p>
          </div>

          <div className="space-y-2.5 text-left">
            <label htmlFor="auth-pin-input" className="sr-only">
              {isSettingPin ? 'PIN baru 4 sampai 6 digit' : 'PIN Authority'}
            </label>
            <input
              id="auth-pin-input"
              type="password"
              inputMode="numeric"
              autoComplete="off"
              autoFocus
              maxLength={6}
              value={pinInput}
              onChange={(e) => setPinInput(cleanDigits(e.target.value))}
              placeholder={isSettingPin ? 'PIN baru (4-6 digit)' : 'Masukkan PIN'}
              disabled={verifying || blocked}
              className="w-full px-3.5 py-2.5 rounded-xl border border-[#B7D6C3] bg-[#FAF8F4] text-center text-lg font-bold tracking-[0.35em] text-[#1C1917] placeholder:text-[#57534E] placeholder:tracking-normal focus:ring-2 focus:ring-[#15803D] disabled:opacity-60"
            />
            {isSettingPin && (
              <>
                <label htmlFor="auth-pin-confirm" className="sr-only">
                  Ulangi PIN baru
                </label>
                <input
                  id="auth-pin-confirm"
                  type="password"
                  inputMode="numeric"
                  autoComplete="off"
                  maxLength={6}
                  value={confirmPin}
                  onChange={(e) => setConfirmPin(cleanDigits(e.target.value))}
                  placeholder="Ulangi PIN"
                  disabled={verifying}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-[#B7D6C3] bg-[#FAF8F4] text-center text-lg font-bold tracking-[0.35em] text-[#1C1917] placeholder:text-[#57534E] placeholder:tracking-normal focus:ring-2 focus:ring-[#15803D] disabled:opacity-60"
                />
              </>
            )}
          </div>

          <button
            type="submit"
            disabled={verifying || blocked}
            className="w-full min-h-[44px] px-4 py-2.5 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-sm font-bold flex items-center justify-center gap-2 disabled:opacity-60 transition"
          >
            <KeyRound className="w-4 h-4" aria-hidden="true" />
            {blocked
              ? `Tunggu ${cooldownSec} detik...`
              : verifying
                ? 'Memproses...'
                : isResetting
                  ? 'Reset & Buka'
                  : isSetup
                    ? 'Simpan & Buka'
                    : 'Buka Kunci'}
          </button>

          {!isSetup && isOwner && (
            <button
              type="button"
              onClick={() => {
                setShowResetPin((v) => !v);
                setPinInput('');
                setConfirmPin('');
              }}
              className="min-h-[44px] px-3 text-xs font-semibold text-[#166534] hover:underline"
            >
              {isResetting ? 'Batal' : 'Lupa PIN? Reset sebagai Owner'}
            </button>
          )}
        </form>
      </div>
    );
  }

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newEmail.trim()) return;
    setSaving(true);
    try {
      await addAuthorityEmail(newEmail, isOwner ? newRole : 'kasir');
      setNewEmail('');
      setNewRole('kasir');
      notify('Email berhasil ditambahkan ke Authority', 'success');
    } catch (err: any) {
      notify(err?.message || 'Gagal menambah email Authority', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleRoleChange = async (email: string, role: AuthorityRole) => {
    setSaving(true);
    try {
      await setAuthorityRole(email, role);
      notify(`Role ${email} diubah ke ${role === 'owner' ? 'Owner' : 'Kasir'}`, 'success');
    } catch (err: any) {
      notify(err?.message || 'Gagal mengubah role', 'error');
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
      <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 shadow-xs">
        <div className="flex flex-wrap items-start justify-between gap-3 mb-2">
          <div className="flex items-center gap-3 min-w-0">
            <div className="p-2.5 rounded-xl bg-[#DCFCE7] text-[#166534] shrink-0">
              <ShieldCheck className="w-5 h-5" aria-hidden="true" />
            </div>
            <div>
              <h2 className="text-lg font-extrabold text-[#1C1917]">
                Authority Login
              </h2>
              <p className="text-xs text-[#57534E]">
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
              className="min-h-[44px] px-3 py-1.5 rounded-xl bg-[#DCEBE2] hover:bg-[#D0E3D8] border border-[#B7D6C3] text-xs font-bold text-[#1C1917] flex items-center gap-1.5 transition"
            >
              <KeyRound className="w-3.5 h-3.5" aria-hidden="true" />
              Ubah PIN
            </button>
            <button
              type="button"
              onClick={() => {
                setUnlocked(false);
                setShowChangePin(false);
              }}
              className="min-h-[44px] px-3 py-1.5 rounded-xl bg-[#DCEBE2] hover:bg-[#D0E3D8] border border-[#B7D6C3] text-xs font-bold text-[#1C1917] flex items-center gap-1.5 transition"
            >
              <Lock className="w-3.5 h-3.5" aria-hidden="true" />
              Kunci
            </button>
          </div>
        </div>

        {showChangePin && (
          <form
            onSubmit={handleChangePin}
            className="mt-3 p-3 rounded-xl bg-[#DCEBE2] border border-[#B7D6C3] flex flex-col sm:flex-row gap-2"
          >
            <label htmlFor="auth-new-pin" className="sr-only">
              PIN baru (4 sampai 6 digit)
            </label>
            <input
              id="auth-new-pin"
              type="password"
              inputMode="numeric"
              autoComplete="off"
              maxLength={6}
              value={newPin}
              onChange={(e) => setNewPin(cleanDigits(e.target.value))}
              placeholder="PIN baru (4-6 digit)"
              className="flex-1 min-w-0 px-3 py-2.5 rounded-xl border border-[#B7D6C3] bg-[#FAF8F4] text-sm text-[#1C1917] placeholder:text-[#57534E] focus:ring-2 focus:ring-[#15803D]"
            />
            <label htmlFor="auth-confirm-new-pin" className="sr-only">
              Ulangi PIN baru
            </label>
            <input
              id="auth-confirm-new-pin"
              type="password"
              inputMode="numeric"
              autoComplete="off"
              maxLength={6}
              value={confirmNewPin}
              onChange={(e) => setConfirmNewPin(cleanDigits(e.target.value))}
              placeholder="Ulangi PIN baru"
              className="flex-1 min-w-0 px-3 py-2.5 rounded-xl border border-[#B7D6C3] bg-[#FAF8F4] text-sm text-[#1C1917] placeholder:text-[#57534E] focus:ring-2 focus:ring-[#15803D]"
            />
            <button
              type="submit"
              disabled={saving}
              className="min-h-[44px] px-4 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold disabled:opacity-60 transition shrink-0"
            >
              Simpan PIN
            </button>
          </form>
        )}

        <form onSubmit={handleAdd} className="mt-4 flex flex-col sm:flex-row gap-2">
          <label htmlFor="auth-new-email" className="sr-only">
            Email Google baru
          </label>
          <input
            id="auth-new-email"
            type="email"
            required
            value={newEmail}
            onChange={(e) => setNewEmail(e.target.value)}
            placeholder="Tambah email Google (contoh: kasir@gmail.com)"
            className="flex-1 min-w-0 px-3.5 py-2.5 rounded-xl border border-[#B7D6C3] bg-[#FAF8F4] text-sm text-[#1C1917] placeholder:text-[#57534E] focus:ring-2 focus:ring-[#15803D]"
          />
          <label htmlFor="auth-new-role" className="sr-only">
            Role akun baru
          </label>
          <select
            id="auth-new-role"
            value={newRole}
            onChange={(e) => setNewRole(e.target.value as AuthorityRole)}
            disabled={saving}
            className="min-h-[44px] px-3 py-2.5 rounded-xl border border-[#B7D6C3] bg-[#FAF8F4] text-sm font-semibold text-[#1C1917] focus:ring-2 focus:ring-[#15803D]"
          >
            <option value="kasir">Kasir</option>
            <option value="owner">Owner</option>
          </select>
          <button
            type="submit"
            disabled={saving}
            className="min-h-[44px] px-4 py-2.5 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-sm font-bold flex items-center justify-center gap-1.5 disabled:opacity-60 transition"
          >
            <Plus className="w-4 h-4" aria-hidden="true" />
            Tambah
          </button>
        </form>
      </div>

      <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] overflow-hidden shadow-xs">
        <div className="px-5 py-3 border-b border-[#B7D6C3] bg-[#DCEBE2] flex items-center justify-between">
          <span className="text-xs font-extrabold uppercase tracking-wider text-[#1C1917]">
            Kolom Authority (Email Diizinkan)
          </span>
          <span className="text-xs font-bold text-[#1C1917]">
            {authorityEmails.length} akun
          </span>
        </div>

        <div className="divide-y divide-[#B7D6C3]">
          {authorityEmails.map((email) => {
            const isCurrent = email === currentEmail;
            const isEmailOwner = ownerEmails.includes(email);
            const isLastOwner = isEmailOwner && ownerEmails.length <= 1;
            const disableDelete =
              saving || authorityEmails.length <= 1 || isLastOwner;
            return (
              <div
                key={email}
                className="px-5 py-3.5 flex flex-wrap items-center justify-between gap-3"
              >
                <div className="min-w-0 flex items-center gap-2 flex-wrap">
                  <span className="text-sm font-semibold text-[#1C1917] truncate">
                    {email}
                  </span>
                  <span
                    className={`px-2 py-0.5 rounded-full text-[10px] font-bold shrink-0 ${
                      isEmailOwner
                        ? 'bg-[#FEF3C7] text-[#92400E] border border-[#FCD34D]'
                        : 'bg-[#DCEBE2] text-[#1C1917] border border-[#B7D6C3]'
                    }`}
                  >
                    {isEmailOwner ? 'Owner' : 'Kasir'}
                  </span>
                  {isCurrent && (
                    <span className="px-2 py-0.5 rounded-full bg-[#DCFCE7] text-[#166534] text-[10px] font-bold shrink-0">
                      Sedang Login
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <select
                    aria-label={`Ubah role untuk ${email}`}
                    value={isEmailOwner ? 'owner' : 'kasir'}
                    onChange={(e) => handleRoleChange(email, e.target.value as AuthorityRole)}
                    disabled={saving || isLastOwner}
                    title={isLastOwner ? 'Minimal harus tersisa 1 Owner' : 'Ubah role akun'}
                    className="min-h-[44px] px-2.5 py-1.5 rounded-xl border border-[#B7D6C3] bg-[#FAF8F4] text-xs font-bold text-[#1C1917] disabled:opacity-45"
                  >
                    <option value="owner">Owner</option>
                    <option value="kasir">Kasir</option>
                  </select>
                  <button
                    type="button"
                    onClick={() => handleRemove(email)}
                    disabled={disableDelete}
                    aria-label={`Hapus ${email}`}
                    title={
                      authorityEmails.length <= 1
                        ? 'Minimal harus tersisa 1 email'
                        : isLastOwner
                          ? 'Minimal harus tersisa 1 Owner'
                          : 'Hapus dari Authority'
                    }
                    className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl text-[#BA1A1A] hover:bg-red-500/10 disabled:opacity-35 transition shrink-0"
                  >
                    <Trash2 className="w-4 h-4" aria-hidden="true" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
