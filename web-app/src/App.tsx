import React, { useState, useEffect, useRef, lazy, Suspense } from 'react';
import { StoreProvider, useStore } from './store';
import { loginWithGoogle, logoutFirebase } from './firebase';
import { daysUntilExpiry } from './types';
import { FeedbackProvider, useFeedback } from './components/Feedback';

const BerandaScreen = lazy(() =>
  import('./components/ScreensPart1').then((m) => ({ default: m.BerandaScreen }))
);
const StokScreen = lazy(() =>
  import('./components/ScreensPart1').then((m) => ({ default: m.StokScreen }))
);
const KasirScreen = lazy(() =>
  import('./components/ScreensPart1').then((m) => ({ default: m.KasirScreen }))
);
const KeuanganScreen = lazy(() =>
  import('./components/ScreensPart2').then((m) => ({ default: m.KeuanganScreen }))
);
const LabaScreen = lazy(() =>
  import('./components/ScreensPart2').then((m) => ({ default: m.LabaScreen }))
);
const PbfScreen = lazy(() =>
  import('./components/ScreensPart2').then((m) => ({ default: m.PbfScreen }))
);
const PengingatScreen = lazy(() =>
  import('./components/ScreensPart2').then((m) => ({ default: m.PengingatScreen }))
);
const RiwayatScreen = lazy(() =>
  import('./components/ScreensPart2').then((m) => ({ default: m.RiwayatScreen }))
);
const BackupAndTutupBukuScreen = lazy(() =>
  import('./components/ScreensPart2').then((m) => ({ default: m.BackupAndTutupBukuScreen }))
);
const AuthorityScreen = lazy(() =>
  import('./components/AuthorityScreen').then((m) => ({ default: m.AuthorityScreen }))
);
import {
  Home,
  Pill,
  ShoppingCart,
  Wallet,
  TrendingUp,
  Truck,
  Bell,
  History,
  Database,
  ShieldCheck,
  Cloud,
  RefreshCw,
  LogIn,
  LogOut,
  Menu,
  X,
} from 'lucide-react';

const NAV_ITEMS = [
  { id: 'beranda', label: 'Beranda', icon: Home },
  { id: 'kasir', label: 'Kasir', icon: ShoppingCart },
  { id: 'stok', label: 'Stok Obat', icon: Pill },
  { id: 'keuangan', label: 'Keuangan', icon: Wallet },
  { id: 'laba', label: 'Laporan Laba', icon: TrendingUp },
  { id: 'pbf', label: 'Faktur PBF', icon: Truck },
  { id: 'pengingat', label: 'Pengingat', icon: Bell },
  { id: 'riwayat', label: 'Riwayat', icon: History },
  { id: 'backup', label: 'Backup & Tutup Buku', icon: Database },
  { id: 'authority', label: 'Authority', icon: ShieldCheck },
];

const MainLayout: React.FC = () => {
  const { user, authLoading, authError: storeAuthError, clearAuthError, syncStatus, syncMessage, syncNow, medicines, purchases } = useStore();
  const { notify } = useFeedback();
  const [activeTab, setActiveTab] = useState('beranda');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [loggingIn, setLoggingIn] = useState(false);
  const [authPill, setAuthPill] = useState<{
    kind: 'login' | 'logout' | 'kick';
    text: string;
  } | null>(null);
  const prevSyncStatus = useRef(syncStatus);
  const authReadyRef = useRef(false);
  const prevUserKeyRef = useRef<string | null>(null);
  const authPillTimerRef = useRef<number | null>(null);

  useEffect(() => {
    if (prevSyncStatus.current === 'PROSES' && syncStatus === 'BERHASIL') {
      notify(syncMessage || 'Sinkronisasi Cloud selesai', 'success');
    } else if (prevSyncStatus.current === 'PROSES' && syncStatus === 'GAGAL') {
      notify(syncMessage || 'Sinkronisasi Cloud gagal', 'error');
    }
    prevSyncStatus.current = syncStatus;
  }, [syncStatus, syncMessage, notify]);

  useEffect(() => {
    return () => {
      if (authPillTimerRef.current) window.clearTimeout(authPillTimerRef.current);
    };
  }, []);

  useEffect(() => {
    if (authLoading) return;
    const currentKey = user ? user.email || user.uid : null;
    if (!authReadyRef.current) {
      authReadyRef.current = true;
      prevUserKeyRef.current = currentKey;
      return;
    }

    const prevKey = prevUserKeyRef.current;
    if (!prevKey && currentKey) {
      if (authPillTimerRef.current) window.clearTimeout(authPillTimerRef.current);
      setAuthPill({
        kind: 'login',
        text: `Masuk: ${user?.email || 'Akun Google'}`,
      });
      authPillTimerRef.current = window.setTimeout(() => setAuthPill(null), 3200);
    } else if (prevKey && !currentKey) {
      if (authPillTimerRef.current) window.clearTimeout(authPillTimerRef.current);
      const isKicked = Boolean(
        storeAuthError &&
        (storeAuthError.includes('Akses dicabut') || storeAuthError.includes('Akses ditolak'))
      );
      setAuthPill(
        isKicked
          ? { kind: 'kick', text: 'Logout otomatis: akses dicabut' }
          : { kind: 'logout', text: 'Anda telah logout' }
      );
      authPillTimerRef.current = window.setTimeout(
        () => setAuthPill(null),
        isKicked ? 4200 : 3200
      );
    }
    prevUserKeyRef.current = currentKey;
  }, [authLoading, user, storeAuthError]);

  const alertCount =
    medicines.filter((m) => m.stock < 10).length +
    medicines.filter((m) => {
      const d = daysUntilExpiry(m.expiryDate);
      return d !== null && d <= 30;
    }).length +
    purchases.filter((p) => p.paymentType === 'KREDIT').length;

  const activeNav = NAV_ITEMS.find((n) => n.id === activeTab) || NAV_ITEMS[0];

  const handleGoogleLogin = async () => {
    setAuthError(null);
    clearAuthError();
    setLoggingIn(true);
    try {
      await loginWithGoogle();
    } catch (e: any) {
      const msg = e?.message || 'Gagal login Google';
      setAuthError(msg);
      notify(msg, 'error');
    } finally {
      setLoggingIn(false);
    }
  };

  const selectTab = (id: string) => {
    setActiveTab(id);
    setMobileMenuOpen(false);
  };

  const authPillTone =
    authPill?.kind === 'login'
      ? {
        wrap: 'bg-[#064e3b] border-emerald-400/50 text-emerald-50',
        dot: 'bg-emerald-400 shadow-[0_0_8px_#34d399]',
      }
      : authPill?.kind === 'kick'
        ? {
          wrap: 'bg-[#7f1d1d] border-red-400/55 text-red-100',
          dot: 'bg-red-400 shadow-[0_0_8px_#f87171]',
        }
        : {
          wrap: 'bg-slate-800 border-amber-400/45 text-amber-100',
          dot: 'bg-amber-400 shadow-[0_0_8px_#fbbf24]',
        };

  const authPillEl = authPill ? (
    <div
      className="no-print fixed top-4 left-1/2 -translate-x-1/2 z-50 pointer-events-none max-w-[calc(100vw-2rem)]"
      role="status"
      aria-live="polite"
    >
      <div
        className={`inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full border text-xs font-semibold shadow-lg whitespace-nowrap max-w-full ${authPillTone.wrap}`}
      >
        <span className={`w-2 h-2 rounded-full shrink-0 ${authPillTone.dot}`} />
        <span className="truncate">{authPill.text}</span>
      </div>
    </div>
  ) : null;

  if (authLoading) {
    return (
      <>
        {authPillEl}
        <div className="min-h-screen bg-[#142E27] text-white flex items-center justify-center p-4">
          <div className="flex flex-col items-center gap-3 text-center">
            <img
              src="/logo-512.jpg"
              alt="Logo Apotek Lestari"
              className="w-14 h-14 rounded-2xl object-cover bg-white shadow-lg"
            />
            <p className="text-sm font-bold text-[#86EFAC]">Memeriksa sesi login...</p>
          </div>
        </div>
      </>
    );
  }

  if (!user) {
    const loginFeatures = [
      { num: '01', text: 'Kasir cepat dengan dukungan Cash, QRIS & Transfer' },
      { num: '02', text: 'Pantau stok menipis & tanggal kedaluwarsa otomatis' },
      { num: '03', text: 'Riwayat penjualan & ekspor laporan Excel instan' },
    ];

    return (
      <>
        {authPillEl}
        <div className="min-h-screen flex flex-col md:grid md:grid-cols-2 bg-[#F7F5F0]">
          {/* Left Forest Showcase Panel */}
          <div className="relative overflow-hidden bg-[radial-gradient(circle_at_top_right,#1F453B_0%,#142E27_58%,#0E201B_100%)] text-white px-5 pt-7 pb-8 rounded-b-3xl md:rounded-none md:px-11 md:py-10 flex flex-col justify-start md:justify-center gap-3.5 md:gap-[26px]">
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-0"
              style={{
                backgroundImage: 'radial-gradient(rgba(255,255,255,0.08) 1px, transparent 1px)',
                backgroundSize: '18px 18px',
              }}
            />

            <div className="relative z-10 flex items-center gap-3.5">
              <div className="w-16 h-16 rounded-2xl bg-white shadow-[0_10px_24px_rgba(0,0,0,0.2)] flex items-center justify-center shrink-0 overflow-hidden">
                <img
                  src="/logo-512.jpg"
                  alt="Logo Apotek Lestari"
                  className="w-full h-full object-cover"
                />
              </div>
              <div>
                <h1 className="text-[22px] font-extrabold tracking-tight text-white leading-tight">
                  Apotek Lestari
                </h1>
                <p className="text-sm font-medium text-[#86EFAC] mt-0.5">
                  Sistem Kasir &amp; Stok Obat
                </p>
              </div>
            </div>

            <h2 className="relative z-10 text-[22px] md:text-[32px] font-extrabold leading-[1.2] tracking-[-0.03em] text-white">
              Kelola kasir, stok, dan laporan dalam satu layar.
            </h2>

            <div className="relative z-10 hidden md:flex md:flex-col md:gap-2.5">
              {loginFeatures.map((item) => (
                <div
                  key={item.num}
                  className="flex items-center gap-3 px-3.5 py-2.5 rounded-xl bg-white/[0.06] border border-white/10 text-[13px] text-white/90"
                >
                  <span className="w-6 h-6 rounded-[7px] bg-[#86EFAC]/15 text-[#86EFAC] text-[11px] font-extrabold flex items-center justify-center shrink-0">
                    {item.num}
                  </span>
                  <span>{item.text}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Right Warm Paper Login Panel */}
          <div className="flex-1 bg-[#F7F5F0] flex items-center justify-center px-4 py-6 md:p-10">
            <div className="w-full max-w-[390px] bg-white border border-[#E6E2D8] rounded-3xl p-6 md:p-8 shadow-[0_16px_40px_rgba(20,46,39,0.06)]">
              <h3 className="text-[22px] font-extrabold tracking-[-0.02em] text-[#111827] mb-1.5">
                Masuk ke Aplikasi
              </h3>
              <p className="text-[13px] leading-[1.55] text-[#6B7280] mb-5">
                Gunakan akun Google terdaftar untuk membuka kasir, stok obat, dan laporan.
              </p>

              {(storeAuthError || authError) && (
                <div className="mb-4 rounded-xl bg-[#FEF2F2] border border-[#FECACA] px-3.5 py-3 flex items-start gap-2.5 text-[#991B1B] text-left">
                  <svg
                    className="w-4 h-4 shrink-0 mt-0.5 text-[#DC2626]"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <circle cx="12" cy="12" r="10" />
                    <line x1="12" y1="8" x2="12" y2="12" />
                    <line x1="12" y1="16" x2="12.01" y2="16" />
                  </svg>
                  <div className="flex flex-col gap-0.5 min-w-0">
                    <span className="text-[12.5px] font-bold leading-tight text-[#991B1B]">
                      Login gagal
                    </span>
                    <span className="text-xs leading-[1.45] text-[#B91C1C] break-words">
                      {storeAuthError || authError}
                    </span>
                  </div>
                </div>
              )}

              <button
                onClick={handleGoogleLogin}
                disabled={loggingIn}
                className="w-full py-3.5 px-4 rounded-[14px] bg-[#142E27] hover:bg-[#1D4036] text-white font-bold text-sm flex items-center justify-center gap-2.5 shadow-[0_8px_20px_rgba(20,46,39,0.22)] transition disabled:opacity-60 cursor-pointer"
              >
                <svg className="w-[18px] h-[18px] shrink-0" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M21.35 11.1h-9.17v2.73h6.51c-.33 3.81-3.5 5.44-6.5 5.44C8.36 19.27 5 16.25 5 12c0-4.1 3.2-7.27 7.2-7.27 3.09 0 4.9 1.97 4.9 1.97L19 4.72S16.56 2 12.1 2C6.42 2 2.03 6.8 2.03 12c0 5.05 4.13 10 10.22 10 5.35 0 9.25-3.67 9.25-9.09 0-1.15-.15-1.81-.15-1.81Z" />
                </svg>
                {loggingIn ? 'Membuka Google...' : 'Login dengan Google'}
              </button>

              <p className="mt-[18px] text-[11.5px] text-[#6B7280] text-center leading-[1.5]">
                Akses dibatasi untuk staf &amp; pemilik Apotek Lestari.
              </p>
            </div>
          </div>
        </div>
      </>
    );
  }

  const MOBILE_BOTTOM_TABS = NAV_ITEMS.filter((n) =>
    ['beranda', 'kasir', 'stok', 'riwayat', 'pengingat'].includes(n.id)
  );

  return (
    <>
      {authPillEl}
      <div className="min-h-screen flex flex-col md:flex-row">
        {/* Compact Sticky Mobile Top Bar */}
        <header className="no-print md:hidden sticky top-0 z-30 bg-[#142E27] text-white px-4 py-3 flex items-center justify-between border-b border-[#244C41]">
          <div className="flex items-center gap-2.5 min-w-0">
            <button
              onClick={() => setMobileMenuOpen(true)}
              className="p-2 rounded-xl bg-white/10 hover:bg-white/20"
              aria-label="Buka Menu"
            >
              <Menu className="w-5 h-5" />
            </button>
            <img
              src="/logo-512.jpg"
              alt="Logo Apotek Lestari"
              className="w-8 h-8 rounded-xl object-cover shrink-0 bg-white"
            />
            <div className="truncate">
              <p className="font-extrabold text-sm leading-tight truncate">
                {activeNav.label}
              </p>
              <p className="text-[10px] text-[#93B5A9] truncate">
                Apotek Lestari • {syncStatus === 'PROSES' ? 'Menyinkronkan...' : syncStatus}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1.5 shrink-0">
            {user && (
              <button
                onClick={() => syncNow(false)}
                disabled={syncStatus === 'PROSES'}
                className="p-2 rounded-xl bg-white/10 hover:bg-white/20"
                title="Sinkron Cloud"
              >
                <RefreshCw
                  className={`w-4 h-4 ${syncStatus === 'PROSES' ? 'animate-spin text-[#86EFAC]' : ''}`}
                />
              </button>
            )}
          </div>
        </header>

        {/* Mobile Drawer Backdrop */}
        {mobileMenuOpen && (
          <div
            onClick={() => setMobileMenuOpen(false)}
            className="no-print md:hidden fixed inset-0 z-40 bg-black/50 backdrop-blur-xs"
          />
        )}

        {/* Sidebar Desktop + Slide-over Drawer Mobile */}
        <aside
          className={`no-print fixed md:sticky md:top-0 md:h-screen inset-y-0 left-0 z-50 w-72 md:w-64 bg-[#142E27] text-[#E7EFEA] flex flex-col justify-between p-4 shrink-0 border-r border-[#244C41] transform transition-transform duration-200 ease-out overflow-y-auto ${mobileMenuOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
            }`}
        >
          <div>
            {/* Brand Identity */}
            <div className="flex items-center justify-between pb-4 mb-4 border-b border-[#244C41]">
              <div className="flex items-center gap-3">
                <img
                  src="/logo-512.jpg"
                  alt="Logo Apotek Lestari"
                  className="w-10 h-10 rounded-xl object-cover shrink-0 bg-white"
                />
                <div>
                  <h1 className="font-extrabold text-base leading-tight text-white">
                    Apotek Lestari
                  </h1>
                  <p className="text-[11px] text-[#93B5A9]">Web & Cloud Pos</p>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => setMobileMenuOpen(false)}
                  className="md:hidden p-2 rounded-xl bg-white/10 hover:bg-white/20"
                  aria-label="Tutup Menu"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Navigation Menu */}
            <nav className="flex flex-col gap-1">
              {NAV_ITEMS.map((item) => {
                const Icon = item.icon;
                const active = activeTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => selectTab(item.id)}
                    className={`flex items-center justify-between px-3 py-2.5 rounded-xl text-[13px] whitespace-nowrap transition ${active
                        ? 'bg-[#15803D] text-white font-semibold shadow-sm'
                        : 'text-[#93B5A9] font-medium hover:bg-white/10 hover:text-white'
                      }`}
                  >
                    <span className="flex items-center gap-2.5 min-w-0">
                      <Icon className="w-4 h-4 shrink-0" />
                      <span className="truncate">{item.label}</span>
                    </span>
                    {item.id === 'pengingat' && alertCount > 0 && (
                      <span className="px-2 py-0.5 rounded-full bg-[#DC2626] text-white text-[10px] font-bold">
                        {alertCount}
                      </span>
                    )}
                  </button>
                );
              })}
            </nav>
          </div>

          {/* Cloud Sync & Google Account Box (Matches OnlineSync.kt) */}
          <div className="mt-4 pt-4 border-t border-[#244C41] space-y-2.5 text-xs">
            <div className="flex items-center justify-between">
              <span className="flex items-center gap-1.5 font-bold text-[#86EFAC]">
                <Cloud className="w-4 h-4" /> Mode Online
              </span>
              <span
                className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${syncStatus === 'BERHASIL'
                    ? 'bg-emerald-500/20 text-emerald-300'
                    : syncStatus === 'PROSES'
                      ? 'bg-sky-500/20 text-sky-300'
                      : syncStatus === 'GAGAL'
                        ? 'bg-red-500/20 text-red-300'
                        : 'bg-white/10 text-white/70'
                  }`}
              >
                {syncStatus}
              </span>
            </div>

            {user ? (
              <div className="space-y-2">
                <p className="truncate text-[11px] text-white/80" title={user.email || ''}>
                  {user.email}
                </p>
                {syncMessage && (
                  <p className="text-[11px] text-[#86EFAC]">{syncMessage}</p>
                )}
                <div className="flex gap-1.5">
                  <button
                    onClick={() => syncNow(false)}
                    disabled={syncStatus === 'PROSES'}
                    className="flex-1 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white font-bold flex items-center justify-center gap-1.5 disabled:opacity-60 transition"
                  >
                    <RefreshCw
                      className={`w-3.5 h-3.5 ${syncStatus === 'PROSES' ? 'animate-spin' : ''
                        }`}
                    />
                    Sinkron
                  </button>
                  <button
                    onClick={() => logoutFirebase()}
                    className="p-2 rounded-xl bg-white/10 hover:bg-red-500/20 text-red-300"
                    title="Keluar Akun"
                  >
                    <LogOut className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ) : (
              <div className="space-y-2">
                <p className="text-[11px] text-white/60">
                  Masuk dengan akun Google yang sama dengan HP Android agar data tersinkron.
                </p>
                {authError && (
                  <p className="text-[11px] text-red-300">{authError}</p>
                )}
                <button
                  onClick={handleGoogleLogin}
                  className="w-full py-2.5 rounded-xl bg-white text-[#142E27] font-extrabold flex items-center justify-center gap-2 hover:bg-[#F6F4EF] transition"
                >
                  <LogIn className="w-4 h-4" /> Masuk dengan Google
                </button>
              </div>
            )}
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="flex-1 min-w-0 p-4 sm:p-6 pb-20 md:pb-6 max-w-7xl w-full mx-auto">
          {syncStatus === 'PROSES' && (
            <div className="no-print mb-4 px-4 py-3 rounded-2xl bg-[#DCFCE7] dark:bg-[#15803D]/20 border border-[#86EFAC] dark:border-[#15803D]/40 text-sm font-bold flex items-center gap-3 text-[#166534] dark:text-[#86EFAC]">
              <RefreshCw className="w-4 h-4 animate-spin shrink-0" />
              <span>Menyinkronkan data dengan Cloud Firebase...</span>
            </div>
          )}
          <Suspense
            fallback={
              <div className="bg-white dark:bg-[#142E27] rounded-2xl border border-[#E7E3D8] dark:border-[#244C41] p-8 flex items-center justify-center gap-3 text-sm font-semibold text-[#78716C] dark:text-[#93B5A9]">
                <RefreshCw className="w-4 h-4 animate-spin text-[#15803D]" />
                <span>Memuat halaman...</span>
              </div>
            }
          >
            {activeTab === 'beranda' && <BerandaScreen onNavigate={selectTab} />}
            {activeTab === 'kasir' && <KasirScreen />}
            {activeTab === 'stok' && <StokScreen />}
            {activeTab === 'keuangan' && <KeuanganScreen />}
            {activeTab === 'laba' && <LabaScreen />}
            {activeTab === 'pbf' && <PbfScreen />}
            {activeTab === 'pengingat' && <PengingatScreen />}
            {activeTab === 'riwayat' && <RiwayatScreen />}
            {activeTab === 'backup' && <BackupAndTutupBukuScreen />}
            {activeTab === 'authority' && <AuthorityScreen />}
          </Suspense>
        </main>

        {/* Mobile Bottom Navigation Bar with SVG Icons */}
        <nav className="no-print md:hidden fixed bottom-0 inset-x-0 z-30 bg-white dark:bg-[#142E27] border-t border-[#E7E3D8] dark:border-[#244C41] px-1 py-1.5 flex items-center justify-around">
          {MOBILE_BOTTOM_TABS.map((item) => {
            const Icon = item.icon;
            const active = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => selectTab(item.id)}
                className={`relative flex flex-col items-center gap-0.5 px-2.5 py-1 rounded-xl text-[10px] font-semibold transition ${active
                    ? 'text-[#15803D] dark:text-[#86EFAC]'
                    : 'text-[#78716C] dark:text-[#93B5A9]'
                  }`}
              >
                <Icon className="w-4 h-4 shrink-0" />
                <span className="truncate max-w-[60px]">{item.id === 'stok' ? 'Stok' : item.label}</span>
                {item.id === 'pengingat' && alertCount > 0 && (
                  <span className="absolute top-0.5 right-1.5 w-2 h-2 rounded-full bg-[#DC2626]" />
                )}
              </button>
            );
          })}
        </nav>
      </div>
    </>
  );
};

export default function App() {
  return (
    <FeedbackProvider>
      <StoreProvider>
        <MainLayout />
      </StoreProvider>
    </FeedbackProvider>
  );
}
