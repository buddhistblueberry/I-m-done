# I-m-done Streaming Platform (Web)

This is the web version of the I-m-done streaming platform, built with React, Express, and tRPC.

## Features

- **TMDB Integration**: Discover trending, now playing, and popular movies and TV shows
- **100+ Streaming Servers**: Support for VidLink, VidSrc, Videasy, and 97+ other providers
- **Advanced Video Player**: HLS/MP4 support with hls.js, server selector, and manual override
- **TV Show Episodes**: Full season and episode browser with metadata
- **Cyberpunk UI**: Neon pink/cyan glow effects with futuristic design
- **Search**: Debounced search with live results
- **Responsive Design**: Works on desktop, tablet, and mobile

## Getting Started

```bash
# Install dependencies
pnpm install

# Start development server
pnpm dev

# Build for production
pnpm build

# Start production server
pnpm start
```

## Architecture

- **Frontend**: React 19 + Tailwind CSS 4 + TypeScript
- **Backend**: Express 4 + tRPC 11 + Node.js
- **Database**: MySQL with Drizzle ORM
- **Video Player**: hls.js for HLS streams
- **API**: TMDB API for content discovery

## Stream Resolution

The platform uses parallel server probing to find working streams:

1. Probes 100+ streaming servers simultaneously
2. Extracts direct HLS/MP4 URLs with proper headers
3. Falls back to iframe embeds if direct extraction fails
4. Allows manual server selection and override
5. Auto-switches on failure

## Deployment

The web app is deployed at: https://imdone-stream-krdvkvy6.manus.space

For production deployment:
1. Set environment variables (TMDB_API_KEY, DATABASE_URL, etc.)
2. Run `pnpm build`
3. Deploy to your hosting platform

## License

MIT
