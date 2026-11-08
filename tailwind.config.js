/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/resources/templates/**/*.html",
    "./src/main/resources/static/js/**/*.js"
  ],
  corePlugins: {
    // preflight desactivado para convivir armónicamente con estilos.css e inventario.css
    preflight: false,
  },
  theme: {
    extend: {
      colors: {
        botica: {
          50: '#e8f4f0',
          100: '#b8dcd2',
          200: '#8ac4b4',
          300: '#5cac96',
          400: '#349479',
          500: '#156552', // color primario
          600: '#0e4b3c', // hover
          700: '#0a362b',
          800: '#06221b',
          900: '#020e0b',
        },
        alerta: {
          critico: '#b42318',
          advertencia: '#b54708',
          exito: '#027a48',
        }
      },
      fontFamily: {
        sans: ['"Inter"', 'system-ui', '-apple-system', 'sans-serif'],
      }
    },
  },
  plugins: [],
};
