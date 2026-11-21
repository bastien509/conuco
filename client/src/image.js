// Photo preparation for the crop doctor. Everything happens on the device:
//  - the photo is redrawn on a canvas, which drops EXIF (GPS position, phone model, time),
//  - it is scaled to 768 px (enough for leaf detail, small enough for Gemini Nano on a CPU),
//  - a quick quality check flags photos that are too dark, too bright or blurred, before any AI runs.

const MAX_SIDE = 768;

export async function preparePhoto(file) {
  const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
  const scale = Math.min(1, MAX_SIDE / Math.max(bmp.width, bmp.height));
  const w = Math.round(bmp.width * scale);
  const h = Math.round(bmp.height * scale);
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const g = c.getContext('2d', { willReadFrequently: true });
  g.drawImage(bmp, 0, 0, w, h);
  bmp.close?.();
  const quality = checkQuality(g, w, h);
  const blob = await new Promise((r) => c.toBlob(r, 'image/jpeg', 0.86));
  const base64 = await blobToBase64(blob);
  const url = URL.createObjectURL(blob);
  return { base64, mimeType: 'image/jpeg', url, width: w, height: h, bytes: blob.size, originalBytes: file.size, quality };
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result).split(',')[1]);
    r.onerror = reject;
    r.readAsDataURL(blob);
  });
}

/** Brightness and sharpness (variance of a Laplacian on a 160 px grey copy). */
function checkQuality(g, w, h) {
  const s = Math.min(1, 160 / Math.max(w, h));
  const sw = Math.max(8, Math.round(w * s));
  const sh = Math.max(8, Math.round(h * s));
  const c = document.createElement('canvas');
  c.width = sw; c.height = sh;
  const cg = c.getContext('2d', { willReadFrequently: true });
  cg.drawImage(g.canvas, 0, 0, sw, sh);
  const px = cg.getImageData(0, 0, sw, sh).data;
  const grey = new Float32Array(sw * sh);
  let total = 0;
  let green = 0;
  for (let i = 0, j = 0; i < px.length; i += 4, j++) {
    grey[j] = 0.299 * px[i] + 0.587 * px[i + 1] + 0.114 * px[i + 2];
    total += grey[j];
    if (px[i + 1] > px[i] * 1.05 && px[i + 1] > px[i + 2] * 1.05) green++;
  }
  const mean = total / grey.length;
  let lapSum = 0;
  let lapSq = 0;
  let n = 0;
  for (let y = 1; y < sh - 1; y++) {
    for (let x = 1; x < sw - 1; x++) {
      const k = y * sw + x;
      const l = 4 * grey[k] - grey[k - 1] - grey[k + 1] - grey[k - sw] - grey[k + sw];
      lapSum += l; lapSq += l * l; n++;
    }
  }
  const sharp = n ? lapSq / n - (lapSum / n) ** 2 : 0;
  const issues = [];
  if (mean < 45) issues.push('dark');
  if (mean > 230) issues.push('bright');
  if (sharp < 60) issues.push('blurry');
  return { brightness: Math.round(mean), sharpness: Math.round(sharp), plantShare: Math.round((green / grey.length) * 100), issues };
}
