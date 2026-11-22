import React, { useState, useEffect, useRef } from 'react';
import { StoreProvider, useStore } from './store';
import { loginWithGoogle, logoutFirebase } from './firebase';
import { daysUntilExpiry } from './types';
import { FeedbackProvider, useFeedback } from './components/Feedback';
import {
  BerandaScreen,
  StokScreen,
  KasirScreen,
} from './components/ScreensPart1';
import {
  KeuanganScreen,
  LabaScreen,
  PbfScreen,
  PengingatScreen,
  RiwayatScreen,
  BackupAndTutupBukuScreen,
} from './components/ScreensPart2';
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
  Cloud,
  RefreshCw,
  LogIn,
  LogOut,
  Sun,
  Moon,
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
];

const MainLayout: React.FC = () => {
  const { user, syncStatus, syncMessage, syncNow, medicines, purchases } = useStore();
  const { notify } = useFeedback();
  const [activeTab, setActiveTab] = useState('beranda');
  const [dark, setDark] = useState(true);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const prevSyncStatus = useRef(syncStatus);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
  }, [dark]);

  useEffect(() => {
    if (prevSyncStatus.current === 'PROSES' && syncStatus === 'BERHASIL') {
      notify(syncMessage || 'Sinkronisasi Cloud selesai', 'success');
    } else if (prevSyncStatus.current === 'PROSES' && syncStatus === 'GAGAL') {
      notify(syncMessage || 'Sinkronisasi Cloud gagal', 'error');
    }
    prevSyncStatus.current = syncStatus;
  }, [syncStatus, syncMessage, notify]);

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
    try {
      await loginWithGoogle();
    } catch (e: any) {
      const msg = e?.message || 'Gagal login Google';
      setAuthError(msg);
      notify(msg, 'error');
    }
  };

  const selectTab = (id: string) => {
    setActiveTab(id);
    setMobileMenuOpen(false);
  };

  return (
    <div className="min-h-screen flex flex-col md:flex-row">
      {/* Compact Sticky Mobile Top Bar */}
      <header className="no-print md:hidden sticky top-0 z-30 bg-[#0B1526] text-white px-4 py-3 flex items-center justify-between border-b border-white/10">
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
            <p className="text-[10px] text-[#9FCAFF] truncate">
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
                className={`w-4 h-4 ${syncStatus === 'PROSES' ? 'animate-spin text-sky-300' : ''}`}
              />
            </button>
          )}
          <button
            onClick={() => setDark(!dark)}
            className="p-2 rounded-xl bg-white/10 hover:bg-white/20"
            title="Ganti Tema Gelap/Terang"
          >
            {dark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </button>
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
        className={`no-print fixed md:static inset-y-0 left-0 z-50 w-72 md:w-64 bg-[#0B1526] text-white flex flex-col justify-between p-4 shrink-0 border-r border-white/10 transform transition-transform duration-200 ease-out overflow-y-auto ${
          mobileMenuOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
        }`}
      >
        <div>
          {/* Brand Identity */}
          <div className="flex items-center justify-between pb-4 mb-4 border-b border-white/10">
            <div className="flex items-center gap-3">
              <img
                src="/logo-512.jpg"
                alt="Logo Apotek Lestari"
                className="w-10 h-10 rounded-2xl object-cover shrink-0 bg-white"
              />
              <div>
                <h1 className="font-extrabold text-base leading-tight">
                  Apotek Lestari
                </h1>
                <p className="text-[11px] text-[#9FCAFF]">Web & Cloud Pos</p>
              </div>
            </div>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setDark(!dark)}
                className="hidden md:inline-flex p-2 rounded-xl bg-white/10 hover:bg-white/20"
                title="Ganti Tema Gelap/Terang"
              >
                {dark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
              </button>
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
          <nav className="flex flex-col gap-1.5">
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon;
              const active = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => selectTab(item.id)}
                  className={`flex items-center justify-between px-3.5 py-2.5 rounded-2xl text-xs font-bold whitespace-nowrap transition ${
                    active
                      ? 'bg-[#0061A4] text-white shadow'
                      : 'text-white/75 hover:bg-white/10'
                  }`}
                >
                  <span className="flex items-center gap-2.5">
                    <Icon className="w-4 h-4" />
                    {item.label}
                  </span>
                  {item.id === 'pengingat' && alertCount > 0 && (
                    <span className="px-2 py-0.5 rounded-full bg-[#BA1A1A] text-white text-[10px]">
                      {alertCount}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        {/* Cloud Sync & Google Account Box (Matches OnlineSync.kt) */}
        <div className="mt-4 pt-4 border-t border-white/10 space-y-2.5 text-xs">
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 font-bold text-[#9FCAFF]">
              <Cloud className="w-4 h-4" /> Mode Online
            </span>
            <span
              className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                syncStatus === 'BERHASIL'
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
                <p className="text-[11px] text-[#7EDC9A]">{syncMessage}</p>
              )}
              <div className="flex gap-1.5">
                <button
                  onClick={() => syncNow(false)}
                  disabled={syncStatus === 'PROSES'}
                  className="flex-1 py-2 rounded-xl bg-[#0061A4] hover:bg-[#00497D] font-bold flex items-center justify-center gap-1.5 disabled:opacity-60"
                >
                  <RefreshCw
                    className={`w-3.5 h-3.5 ${
                      syncStatus === 'PROSES' ? 'animate-spin' : ''
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
                className="w-full py-2.5 rounded-xl bg-white text-[#0B1526] font-extrabold flex items-center justify-center gap-2 hover:bg-gray-100 transition"
              >
                <LogIn className="w-4 h-4" /> Masuk dengan Google
              </button>
            </div>
          )}
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 p-4 sm:p-6 max-w-7xl w-full mx-auto">
        {syncStatus === 'PROSES' && (
          <div className="no-print mb-4 px-4 py-3 rounded-2xl bg-[#0061A4]/15 border border-[#0061A4]/30 text-sm font-bold flex items-center gap-3 text-[#0061A4] dark:text-[#9FCAFF]">
            <RefreshCw className="w-4 h-4 animate-spin shrink-0" />
            <span>Menyinkronkan data dengan Cloud Firebase...</span>
          </div>
        )}
        {activeTab === 'beranda' && <BerandaScreen onNavigate={selectTab} />}
        {activeTab === 'kasir' && <KasirScreen />}
        {activeTab === 'stok' && <StokScreen />}
        {activeTab === 'keuangan' && <KeuanganScreen />}
        {activeTab === 'laba' && <LabaScreen />}
        {activeTab === 'pbf' && <PbfScreen />}
        {activeTab === 'pengingat' && <PengingatScreen />}
        {activeTab === 'riwayat' && <RiwayatScreen />}
        {activeTab === 'backup' && <BackupAndTutupBukuScreen />}
      </main>
    </div>
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
