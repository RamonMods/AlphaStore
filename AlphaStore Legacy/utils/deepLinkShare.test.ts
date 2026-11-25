import { describe, expect, it } from 'vitest';
import { BADGE_BASE_URL, buildDeepLinkShareArtifacts } from './deepLinkShare';

describe('deep link share helpers', () => {
  it('builds encoded deep and web links from the app id', () => {
    const result = buildDeepLinkShareArtifacts('plex android/tv');

    expect(result.deepLink).toBe('alphastore://app/plex%20android%2Ftv');
    expect(result.webLink).toBe(
      `${BADGE_BASE_URL}/redirect.html?id=plex%20android%2Ftv`
    );
  });

  it('builds markdown and html badge snippets from the same badge asset', () => {
    const result = buildDeepLinkShareArtifacts('app-123');

    expect(result.badgeImgUrl).toBe(`https://raw.githubusercontent.com/RamonMods/AlphaStore/refs/heads/main/assets/alphastore-badge.png`);
    expect(result.mdBadge).toBe(
      `[![Get it on AlphaStore](https://raw.githubusercontent.com/RamonMods/AlphaStore/refs/heads/main/assets/alphastore-badge.png)](${BADGE_BASE_URL}/redirect.html?id=app-123)`
    );
    expect(result.htmlBadge).toBe(
      `<a href="${BADGE_BASE_URL}/redirect.html?id=app-123">\n  <img src="https://raw.githubusercontent.com/RamonMods/AlphaStore/refs/heads/main/assets/alphastore-badge.png" alt="Get it on AlphaStore" height="60">\n</a>`
    );
  });
});
