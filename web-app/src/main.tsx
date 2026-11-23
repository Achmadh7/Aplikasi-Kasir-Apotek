import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

const host = window.location.hostname;
if (host === 'apoteklestari.web.app' || host === 'apoteklestari.firebaseapp.com') {
  window.location.replace(
    `https://apoteklestari.web.id${window.location.pathname}${window.location.search}${window.location.hash}`
  );
} else {
  if ('serviceWorker' in navigator && (import.meta as any).env?.PROD) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch(() => undefined);
    });
  }

  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}
