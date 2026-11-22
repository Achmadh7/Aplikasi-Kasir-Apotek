import React, { useState } from 'react';
import { ShieldCheck, Plus, Trash2 } from 'lucide-react';
import { useStore } from '../store';
import { useFeedback } from './Feedback';
import { normalizeEmail } from '../firebase';

export const AuthorityScreen: React.FC = () => {
  const { user, authorityEmails, addAuthorityEmail, removeAuthorityEmail } = useStore();
  const { notify } = useFeedback();
  const [newEmail, setNewEmail] = useState('');
  const [saving, setSaving] = useState(false);

  const currentEmail = normalizeEmail(user?.email);

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
      <div className="rounded-2xl bg-white dark:bg-[#142E27] border border-[#E7E3D8] dark:border-[#244C41] p-5 shadow-sm">
        <div className="flex items-center gap-3 mb-2">
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

      <div className="rounded-2xl bg-white dark:bg-[#101F38] border border-slate-200 dark:border-white/10 overflow-hidden shadow-sm">
        <div className="px-5 py-3 border-b border-slate-200 dark:border-white/10 flex items-center justify-between">
          <span className="text-xs font-extrabold uppercase tracking-wider text-slate-500 dark:text-[#86EFAC]">
            Kolom Authority (Email Diizinkan)
          </span>
          <span className="text-xs font-bold text-slate-500 dark:text-white/60">
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
                  <span className="text-sm font-semibold text-slate-900 dark:text-white truncate">
                    {email}
                  </span>
                  {isCurrent && (
                    <span className="px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 text-[10px] font-bold shrink-0">
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
