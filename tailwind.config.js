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
        // Paleta Institucional Pino (Bocetos finales Botica San Miguel)
        pine: {
          50: '#e9f4f0',
          100: '#d3ebe3',
          200: '#a7d7c7',
          300: '#70bfa8',
          400: '#3ea689',
          500: '#1b7f66',
          600: '#1b7f66',
          700: '#166a55', // color primario corporativo (WCAG AA > 6:1 contra blanco)
          800: '#143a31', // gradiente superior de sidebar
          900: '#0f2a24', // gradiente inferior de sidebar
        },
        ink: {
          DEFAULT: '#10201b',
          2: '#33463f',
          3: '#5b6d66',
        },
        line: '#dfe6e3',
        surface: '#f3f5f4',
        card: '#ffffff',
        // Estados semánticos (Contraste AA verificado >= 4.5:1)
        amber: {
          DEFAULT: '#b45309',
          500: '#f59e0b', // Botón Deshacer (Pila AyE)
          bg: '#fef3c7',
        },
        rose: {
          DEFAULT: '#be123c', // Peligro, vencidos, agotados
          bg: '#ffe4e6',
        },
        blue: {
          DEFAULT: '#1d4ed8', // Movimientos, reposición
          bg: '#e0ecff',
        },
        ok: {
          DEFAULT: '#047857', // Éxito, existencias óptimas
          bg: '#d1fae5',
        },
        // Compatibilidad hacia atrás con clases previas
        botica: {
          50: '#e8f4f0',
          100: '#b8dcd2',
          200: '#8ac4b4',
          300: '#5cac96',
          400: '#349479',
          500: '#166a55',
          600: '#143a31',
          700: '#0f2a24',
          800: '#06221b',
          900: '#020e0b',
        },
        alerta: {
          critico: '#be123c',
          advertencia: '#b45309',
          exito: '#047857',
        }
      },
      fontFamily: {
        sans: ['"Inter"', '"Segoe UI"', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['ui-monospace', '"SF Mono"', 'Menlo', 'Consolas', 'monospace'],
      },
      boxShadow: {
        'card': '0 1px 2px rgba(16,32,27,.04), 0 6px 18px -10px rgba(16,32,27,.12)',
        'modal': '0 30px 80px -20px rgba(0,0,0,.5)',
      },
      borderRadius: {
        '2xl': '16px',
        '3xl': '20px',
      }
    },
  },
  plugins: [],
};
