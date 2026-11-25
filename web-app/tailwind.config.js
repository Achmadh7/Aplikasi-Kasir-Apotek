/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Plus Jakarta Sans"', 'ui-sans-serif', 'system-ui', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'Roboto', 'sans-serif'],
      },
      colors: {
        navy: {
          900: '#0B1526',
          800: '#111418',
          700: '#1D2024',
          600: '#272A2F',
        },
        brand: {
          DEFAULT: '#0061A4',
          light: '#9FCAFF',
          dark: '#00497D'
        }
      }
    }
  },
  plugins: []
};
