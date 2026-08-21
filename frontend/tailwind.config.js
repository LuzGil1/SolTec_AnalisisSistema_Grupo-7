/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        'brand-bg': '#faf5ff',
        'brand-text-dark': '#4c1d95',
        'brand-text-muted': '#7e22ce',
        'brand-purple': '#a855f7',
        'brand-lilac': '#c084fc',
        'brand-lavender': '#e9d5ff',
      },
      fontFamily: {
        sans: ['Poppins', 'sans-serif'],
      },
      backgroundImage: {
        'gradient-primary': 'linear-gradient(135deg, #a855f7 0%, #c084fc 100%)',
        'gradient-text': 'linear-gradient(to right, #7e22ce, #9333ea)',
      },
      animation: {
        'blob': 'blob 7s infinite',
      },
      keyframes: {
        blob: {
          '0%': { transform: 'translate(0px, 0px) scale(1)' },
          '33%': { transform: 'translate(30px, -50px) scale(1.1)' },
          '66%': { transform: 'translate(-20px, 20px) scale(0.9)' },
          '100%': { transform: 'translate(0px, 0px) scale(1)' },
        }
      }
    }
  },
  plugins: [],
}
