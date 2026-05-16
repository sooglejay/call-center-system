/**
 * Build an APK download URL that follows the current web origin.
 *
 * Older app_versions rows store absolute URLs generated at publish time, e.g.
 * https://old-domain.com/uploads/apk/app-release-1.apk. When the web domain
 * changes, using that stored URL directly keeps sending users to the old domain.
 * For APK files served by this system, keep only the path and rebuild the URL
 * from the current browser origin.
 */
export const getApkDownloadUrl = (apkUrl?: string | null, downloadUrl?: string | null): string => {
  const rawUrl = apkUrl || downloadUrl || '';

  if (!rawUrl) {
    return '';
  }

  try {
    const parsedUrl = new URL(rawUrl, window.location.origin);
    const apkPath = parsedUrl.pathname;

    if (apkPath.startsWith('/uploads/apk/')) {
      const basePath = import.meta.env.VITE_BASE_PATH || '';
      const normalizedBasePath = basePath && basePath !== '/'
        ? `/${basePath.replace(/^\/+|\/+$/g, '')}`
        : '';

      return `${window.location.origin}${normalizedBasePath}${apkPath}${parsedUrl.search}${parsedUrl.hash}`;
    }

    return parsedUrl.href;
  } catch (error) {
    return rawUrl;
  }
};
