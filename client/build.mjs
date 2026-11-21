// Bundles the Conuco web app (Firebase JS SDK + Leaflet + app code) into web/assets/conuco.js.
import { build } from 'esbuild';
import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
await build({
  entryPoints: [join(here, 'src/app.js')], outfile: join(here, '../web/assets/conuco.js'),
  bundle: true, minify: true, format: 'esm', target: ['chrome120', 'safari16'], legalComments: 'none', logLevel: 'warning',
});
mkdirSync(join(here, '../web/vendor'), { recursive: true });
copyFileSync(join(here, 'node_modules/leaflet/dist/leaflet.css'), join(here, '../web/vendor/leaflet.css'));
console.log('built web/assets/conuco.js');
