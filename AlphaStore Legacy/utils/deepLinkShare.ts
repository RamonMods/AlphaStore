export const BADGE_BASE_URL = 'https://ramonmods.github.io/AlphaStore-Data';

interface DeepLinkShareArtifacts {
  deepLink: string;
  webLink: string;
  badgeImgUrl: string;
  mdBadge: string;
  htmlBadge: string;
}

export function buildDeepLinkShareArtifacts(appId: string, appIconUrl?: string): DeepLinkShareArtifacts {
  const encodedId = encodeURIComponent(appId);
  const encodedIcon = appIconUrl?.trim() ? `&icon=${encodeURIComponent(appIconUrl.trim())}` : '';
  const deepLink = `alphastore://app/${encodedId}`;
  const webLink = `${BADGE_BASE_URL}/redirect.html?id=${encodedId}${encodedIcon}`;
  const badgeImgUrl = `https://raw.githubusercontent.com/RamonMods/AlphaStore/refs/heads/main/assets/alphastore-badge.png`;

  return {
    deepLink,
    webLink,
    badgeImgUrl,
    mdBadge: `[![Get it on AlphaStore](${badgeImgUrl})](${webLink})`,
    htmlBadge: `<a href="${webLink}">\n  <img src="${badgeImgUrl}" alt="Get it on AlphaStore" height="60">\n</a>`
  };
}
