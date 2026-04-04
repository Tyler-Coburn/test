import { memo, useCallback, useState } from "react";
import { type ComponentProcessProps } from "components/system/Apps/RenderComponent";
import {
  StyledBarChart,
  StyledButton,
  StyledCard,
  StyledContent,
  StyledDigestCard,
  StyledFeedItem,
  StyledHeader,
  StyledLinkChip,
  StyledMapPlaceholder,
  StyledNetworkNode,
  StyledProBanner,
  StyledProfileCard,
  StyledSectionTitle,
  StyledStatCard,
  StyledStatGrid,
  StyledTab,
  StyledTabs,
  StyledTapID,
} from "components/apps/TapID/StyledTapID";

// ─── Types ────────────────────────────────────────────────────────────────────

type Tab = "dashboard" | "profiles" | "analytics" | "network" | "settings";

type FeedItem = {
  id: number;
  type: "tap" | "view" | "click" | "save" | "mutual";
  title: string;
  meta: string;
  time: string;
  color: string;
  bgColor: string;
};

type Profile = {
  id: string;
  name: string;
  emoji: string;
  description: string;
  links: Array<{ label: string; color: string }>;
  tapCount: number;
  active: boolean;
};

type Connection = {
  id: number;
  name: string;
  initials: string;
  mutual: string;
  location: string;
  c1: string;
  c2: string;
};

// ─── Static Data ──────────────────────────────────────────────────────────────

const FEED_ITEMS: FeedItem[] = [
  {
    id: 1,
    type: "tap",
    title: "Alex Liu tapped your card",
    meta: "Conference Mode • San Francisco, CA",
    time: "2m ago",
    color: "#6366f1",
    bgColor: "rgba(99,102,241,0.15)",
  },
  {
    id: 2,
    type: "view",
    title: "Maya Kim viewed your profile",
    meta: "Clicked LinkedIn & Calendly",
    time: "14m ago",
    color: "#10b981",
    bgColor: "rgba(16,185,129,0.15)",
  },
  {
    id: 3,
    type: "save",
    title: "Jordan Lee saved your contact",
    meta: "Social Mode • New York, NY",
    time: "1h ago",
    color: "#f59e0b",
    bgColor: "rgba(245,158,11,0.15)",
  },
  {
    id: 4,
    type: "click",
    title: "Your Instagram link was clicked",
    meta: "3 clicks from 2 profiles today",
    time: "2h ago",
    color: "#ec4899",
    bgColor: "rgba(236,72,153,0.15)",
  },
  {
    id: 5,
    type: "mutual",
    title: "You and Sam Park both know Taylor Chen",
    meta: "Mutual connection discovered",
    time: "3h ago",
    color: "#8b5cf6",
    bgColor: "rgba(139,92,246,0.15)",
  },
  {
    id: 6,
    type: "tap",
    title: "Riley Johnson tapped your card",
    meta: "Social Mode • Austin, TX",
    time: "5h ago",
    color: "#6366f1",
    bgColor: "rgba(99,102,241,0.15)",
  },
  {
    id: 7,
    type: "view",
    title: "Casey Morgan viewed your profile",
    meta: "Clicked Portfolio & Twitter",
    time: "7h ago",
    color: "#10b981",
    bgColor: "rgba(16,185,129,0.15)",
  },
];

const INITIAL_PROFILES: Profile[] = [
  {
    id: "conference",
    name: "Conference Mode",
    emoji: "💼",
    description: "Professional networking at events & conferences",
    links: [
      { label: "LinkedIn", color: "rgba(10,102,194,0.25)" },
      { label: "Calendly", color: "rgba(0,110,235,0.25)" },
      { label: "Portfolio", color: "rgba(99,102,241,0.25)" },
    ],
    tapCount: 47,
    active: true,
  },
  {
    id: "social",
    name: "Social Mode",
    emoji: "🎉",
    description: "Casual social meetups, parties & concerts",
    links: [
      { label: "Instagram", color: "rgba(225,48,108,0.25)" },
      { label: "Spotify", color: "rgba(30,215,96,0.25)" },
      { label: "Venmo", color: "rgba(9,116,217,0.25)" },
    ],
    tapCount: 31,
    active: false,
  },
  {
    id: "creative",
    name: "Creative Mode",
    emoji: "🎨",
    description: "Art galleries, creative spaces & studios",
    links: [
      { label: "Behance", color: "rgba(0,103,255,0.25)" },
      { label: "Dribbble", color: "rgba(234,76,137,0.25)" },
      { label: "YouTube", color: "rgba(255,0,0,0.2)" },
    ],
    tapCount: 12,
    active: false,
  },
  {
    id: "dating",
    name: "Dating Mode",
    emoji: "💫",
    description: "Personal connections, dating apps & events",
    links: [
      { label: "Instagram", color: "rgba(225,48,108,0.25)" },
      { label: "Spotify", color: "rgba(30,215,96,0.25)" },
    ],
    tapCount: 8,
    active: false,
  },
];

const CONNECTIONS: Connection[] = [
  {
    id: 1,
    name: "Alex Liu",
    initials: "AL",
    mutual: "You both know Maya Kim & Taylor Chen",
    location: "San Francisco, CA",
    c1: "#6366f1",
    c2: "#8b5cf6",
  },
  {
    id: 2,
    name: "Maya Kim",
    initials: "MK",
    mutual: "You both know Alex Liu & Jordan Lee",
    location: "New York, NY",
    c1: "#10b981",
    c2: "#059669",
  },
  {
    id: 3,
    name: "Jordan Lee",
    initials: "JL",
    mutual: "You both know Sam Park",
    location: "Austin, TX",
    c1: "#f59e0b",
    c2: "#d97706",
  },
  {
    id: 4,
    name: "Sam Park",
    initials: "SP",
    mutual: "You both know Taylor Chen & Riley Johnson",
    location: "Seattle, WA",
    c1: "#ec4899",
    c2: "#db2777",
  },
  {
    id: 5,
    name: "Taylor Chen",
    initials: "TC",
    mutual: "You both know Alex Liu",
    location: "Chicago, IL",
    c1: "#8b5cf6",
    c2: "#7c3aed",
  },
];

const TAP_CITIES = [
  "SF",
  "NY",
  "LA",
  "CHI",
  "ATX",
  "SEA",
  "MIA",
  "BOS",
];

// ─── Icon SVGs (inline) ───────────────────────────────────────────────────────

const TapIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M12 2a10 10 0 1 0 10 10" />
    <path d="M12 6a6 6 0 0 1 6 6" />
    <path d="M12 10a2 2 0 0 1 2 2" />
    <circle cx="12" cy="12" r="1" fill="currentColor" />
  </svg>
);

const ActivityIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>
);

const ProfilesIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="8" r="4" />
    <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
  </svg>
);

const AnalyticsIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <rect x="3" y="13" width="4" height="8" />
    <rect x="10" y="9" width="4" height="12" />
    <rect x="17" y="5" width="4" height="16" />
  </svg>
);

const NetworkIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="5" r="2" />
    <circle cx="5" cy="19" r="2" />
    <circle cx="19" cy="19" r="2" />
    <path d="M12 7v3m-5.7 6.5L10 14m4 0 3.7 2.5" />
  </svg>
);

const SettingsIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
  </svg>
);

const FeedTypeIcon = ({ type }: { type: FeedItem["type"] }) => {
  switch (type) {
    case "tap":
      return (
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path d="M18 8h1a4 4 0 0 1 0 8h-1" />
          <path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4Z" />
          <line x1="6" y1="1" x2="6" y2="4" />
          <line x1="10" y1="1" x2="10" y2="4" />
          <line x1="14" y1="1" x2="14" y2="4" />
        </svg>
      );
    case "view":
      return (
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
          <circle cx="12" cy="12" r="3" />
        </svg>
      );
    case "save":
      return (
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
          <polyline points="17 21 17 13 7 13 7 21" />
          <polyline points="7 3 7 8 15 8" />
        </svg>
      );
    case "click":
      return (
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
      );
    default:
      return (
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
      );
  }
};

// ─── Tab Views ────────────────────────────────────────────────────────────────

const DashboardTab = () => {
  const [notifEnabled, setNotifEnabled] = useState(true);

  return (
    <>
      <StyledDigestCard>
        <div className="digest-header">
          <span className="digest-icon">📊</span>
          <span className="digest-title">Weekly Digest</span>
          <span className="digest-period">Apr 1–7, 2025</span>
        </div>
        <div className="digest-stats">
          <div className="digest-stat">
            <div className="digest-stat-value">47</div>
            <div className="digest-stat-label">Profile Views</div>
          </div>
          <div className="digest-stat">
            <div className="digest-stat-value">89</div>
            <div className="digest-stat-label">Link Clicks</div>
          </div>
          <div className="digest-stat">
            <div className="digest-stat-value">12</div>
            <div className="digest-stat-label">New Taps</div>
          </div>
        </div>
      </StyledDigestCard>

      <StyledStatGrid>
        <StyledStatCard $color="#6366f1">
          <div className="stat-value">12</div>
          <div className="stat-label">Taps Today</div>
          <div className="stat-delta">↑ 4 vs yesterday</div>
        </StyledStatCard>
        <StyledStatCard $color="#10b981">
          <div className="stat-value">89</div>
          <div className="stat-label">Profile Views</div>
          <div className="stat-delta">↑ 12 this week</div>
        </StyledStatCard>
        <StyledStatCard $color="#f59e0b">
          <div className="stat-value">34</div>
          <div className="stat-label">Links Clicked</div>
          <div className="stat-delta">↑ 8 this week</div>
        </StyledStatCard>
        <StyledStatCard $color="#ec4899">
          <div className="stat-value">🔥 7</div>
          <div className="stat-label">Day Streak</div>
          <div className="stat-delta">Personal best!</div>
        </StyledStatCard>
      </StyledStatGrid>

      <StyledCard>
        <div
          style={{
            alignItems: "center",
            display: "flex",
            justifyContent: "space-between",
            marginBottom: "12px",
          }}
        >
          <StyledSectionTitle style={{ margin: 0 }}>
            Activity Pulse
          </StyledSectionTitle>
          <label
            style={{
              alignItems: "center",
              cursor: "pointer",
              display: "flex",
              fontSize: "11px",
              gap: "6px",
            }}
          >
            <span style={{ color: "rgba(232,234,246,0.5)" }}>Notify</span>
            <div
              style={{
                background: notifEnabled
                  ? "#6366f1"
                  : "rgba(255,255,255,0.1)",
                borderRadius: "10px",
                cursor: "pointer",
                height: "18px",
                position: "relative",
                transition: "background 0.2s",
                width: "32px",
              }}
              onClick={() => setNotifEnabled((v) => !v)}
            >
              <div
                style={{
                  background: "#fff",
                  borderRadius: "50%",
                  height: "12px",
                  left: notifEnabled ? "17px" : "3px",
                  position: "absolute",
                  top: "3px",
                  transition: "left 0.2s",
                  width: "12px",
                }}
              />
            </div>
          </label>
        </div>
        {FEED_ITEMS.map((item) => (
          <StyledFeedItem key={item.id}>
            <div
              className="feed-icon"
              style={{
                background: item.bgColor,
                color: item.color,
              }}
            >
              <FeedTypeIcon type={item.type} />
            </div>
            <div className="feed-body">
              <div className="feed-title">{item.title}</div>
              <div className="feed-meta">{item.meta}</div>
            </div>
            <div className="feed-time">{item.time}</div>
          </StyledFeedItem>
        ))}
      </StyledCard>
    </>
  );
};

const ProfilesTab = () => {
  const [profiles, setProfiles] = useState<Profile[]>(INITIAL_PROFILES);

  const activateProfile = useCallback((id: string) => {
    setProfiles((prev) =>
      prev.map((p) => ({ ...p, active: p.id === id }))
    );
  }, []);

  return (
    <>
      <StyledCard>
        <StyledSectionTitle>Smart Context Profiles</StyledSectionTitle>
        <p
          style={{
            color: "rgba(232,234,246,0.5)",
            fontSize: "12px",
            lineHeight: "1.6",
            marginBottom: "14px",
          }}
        >
          Tap a profile to activate it. Each mode shows different links tailored
          to the context — conferences, parties, creative spaces, and more.
        </p>
        {profiles.map((profile) => (
          <StyledProfileCard
            key={profile.id}
            $active={profile.active}
            onClick={() => activateProfile(profile.id)}
          >
            <div className="profile-header">
              <span className="profile-mode-icon">{profile.emoji}</span>
              <div className="profile-info">
                <div className="profile-name">{profile.name}</div>
                <div className="profile-desc">{profile.description}</div>
              </div>
              <div className="profile-status">
                {profile.active && <div className="active-indicator" />}
                <div className="tap-count">{profile.tapCount} taps</div>
              </div>
            </div>
            <div className="profile-links">
              {profile.links.map((link) => (
                <StyledLinkChip key={link.label} $color={link.color}>
                  {link.label}
                </StyledLinkChip>
              ))}
            </div>
          </StyledProfileCard>
        ))}
      </StyledCard>

      <StyledCard>
        <StyledSectionTitle>Profile Link Scheduling</StyledSectionTitle>
        <p
          style={{
            color: "rgba(232,234,246,0.5)",
            fontSize: "12px",
            lineHeight: "1.6",
            marginBottom: "12px",
          }}
        >
          Set rules for when specific links appear — e.g. show Calendly only
          on weekdays, Venmo only on weekends.
        </p>
        {[
          {
            link: "Calendly",
            rule: "Mon–Fri, 9:00 AM – 6:00 PM",
            enabled: true,
          },
          { link: "Venmo", rule: "Weekends only", enabled: true },
          {
            link: "Portfolio",
            rule: "Always visible",
            enabled: false,
          },
        ].map((item) => (
          <div
            key={item.link}
            style={{
              alignItems: "center",
              borderTop: "1px solid rgba(255,255,255,0.05)",
              display: "flex",
              gap: "10px",
              padding: "10px 0",
            }}
          >
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: "13px", fontWeight: 600 }}>
                {item.link}
              </div>
              <div
                style={{
                  color: "rgba(232,234,246,0.45)",
                  fontSize: "11px",
                  marginTop: "2px",
                }}
              >
                {item.rule}
              </div>
            </div>
            <StyledLinkChip
              $color={
                item.enabled
                  ? "rgba(16,185,129,0.2)"
                  : "rgba(255,255,255,0.1)"
              }
            >
              {item.enabled ? "⏰ Active" : "Always"}
            </StyledLinkChip>
          </div>
        ))}
        <div style={{ marginTop: "12px" }}>
          <StyledButton $variant="secondary" $small>
            + Add Schedule Rule
          </StyledButton>
        </div>
      </StyledCard>

      <StyledCard>
        <StyledSectionTitle>Magic Link Preview</StyledSectionTitle>
        <p
          style={{
            color: "rgba(232,234,246,0.5)",
            fontSize: "12px",
            lineHeight: "1.6",
            marginBottom: "12px",
          }}
        >
          When someone taps your card, they see a smart landing page that
          captures their info before showing your profile.
        </p>
        <div
          style={{
            background: "#fff",
            borderRadius: "10px",
            color: "#111",
            fontFamily: "system-ui, sans-serif",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              background: "linear-gradient(135deg, #6366f1, #8b5cf6)",
              color: "#fff",
              padding: "16px",
              textAlign: "center",
            }}
          >
            <div
              style={{
                fontSize: "13px",
                fontWeight: 700,
                marginBottom: "4px",
              }}
            >
              Jordan shared their TapID with you
            </div>
            <div style={{ fontSize: "11px", opacity: 0.85 }}>
              Save their contact — takes 10 seconds
            </div>
          </div>
          <div style={{ padding: "12px" }}>
            <div
              style={{
                background: "#f5f5f5",
                borderRadius: "6px",
                fontSize: "11px",
                marginBottom: "8px",
                padding: "8px",
              }}
            >
              📧 Enter your email to save this contact
            </div>
            <div
              style={{
                background: "#6366f1",
                borderRadius: "6px",
                color: "#fff",
                fontSize: "11px",
                fontWeight: 600,
                padding: "8px",
                textAlign: "center",
              }}
            >
              Save Contact & Download TapID
            </div>
          </div>
        </div>
      </StyledCard>
    </>
  );
};

const AnalyticsTab = () => (
  <>
    <StyledCard>
      <StyledSectionTitle>Link Performance</StyledSectionTitle>
      <StyledBarChart>
        {[
          { label: "Instagram", value: 89, max: 89, color: "#ec4899" },
          { label: "LinkedIn", value: 72, max: 89, color: "#0a66c2" },
          { label: "Portfolio", value: 54, max: 89, color: "#6366f1" },
          { label: "Calendly", value: 38, max: 89, color: "#00adef" },
          { label: "Spotify", value: 21, max: 89, color: "#1db954" },
          { label: "Venmo", value: 14, max: 89, color: "#0974d7" },
        ].map((item) => (
          <div key={item.label} className="bar-row">
            <span className="bar-label">{item.label}</span>
            <div className="bar-track">
              <div
                className="bar-fill"
                style={{
                  background: item.color,
                  width: `${(item.value / item.max) * 100}%`,
                }}
              />
            </div>
            <span className="bar-value">{item.value}</span>
          </div>
        ))}
      </StyledBarChart>
    </StyledCard>

    <StyledCard>
      <StyledSectionTitle>Tap Activity (Last 7 Days)</StyledSectionTitle>
      <div
        style={{
          alignItems: "flex-end",
          display: "flex",
          gap: "6px",
          height: "80px",
          justifyContent: "space-between",
        }}
      >
        {[
          { day: "Mon", taps: 3 },
          { day: "Tue", taps: 7 },
          { day: "Wed", taps: 5 },
          { day: "Thu", taps: 12 },
          { day: "Fri", taps: 9 },
          { day: "Sat", taps: 6 },
          { day: "Sun", taps: 4 },
        ].map((item) => (
          <div
            key={item.day}
            style={{
              alignItems: "center",
              display: "flex",
              flex: 1,
              flexDirection: "column",
              gap: "4px",
            }}
          >
            <div
              style={{
                background: "linear-gradient(to top, #6366f1, #8b5cf6)",
                borderRadius: "3px 3px 0 0",
                height: `${(item.taps / 12) * 60}px`,
                minHeight: "4px",
                width: "100%",
              }}
            />
            <span
              style={{
                color: "rgba(232,234,246,0.4)",
                fontSize: "9px",
              }}
            >
              {item.day}
            </span>
          </div>
        ))}
      </div>
    </StyledCard>

    <StyledCard>
      <StyledSectionTitle>Tap Locations</StyledSectionTitle>
      <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
        {[
          { city: "San Francisco, CA", count: 18, pct: 100 },
          { city: "New York, NY", count: 14, pct: 78 },
          { city: "Austin, TX", count: 9, pct: 50 },
          { city: "Seattle, WA", count: 5, pct: 28 },
          { city: "Chicago, IL", count: 3, pct: 17 },
        ].map((item) => (
          <div
            key={item.city}
            style={{ alignItems: "center", display: "flex", gap: "10px" }}
          >
            <span
              style={{
                color: "rgba(232,234,246,0.7)",
                fontSize: "11px",
                minWidth: "110px",
              }}
            >
              {item.city}
            </span>
            <div
              style={{
                background: "rgba(255,255,255,0.06)",
                borderRadius: "3px",
                flex: 1,
                height: "6px",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  background: "#6366f1",
                  borderRadius: "3px",
                  height: "100%",
                  width: `${item.pct}%`,
                }}
              />
            </div>
            <span
              style={{
                color: "rgba(232,234,246,0.5)",
                fontSize: "11px",
                minWidth: "20px",
                textAlign: "right",
              }}
            >
              {item.count}
            </span>
          </div>
        ))}
      </div>
    </StyledCard>

    <StyledCard>
      <StyledSectionTitle>Device & Time Breakdown</StyledSectionTitle>
      <div
        style={{
          display: "grid",
          gap: "10px",
          gridTemplateColumns: "1fr 1fr",
        }}
      >
        {[
          {
            label: "iOS",
            value: "62%",
            color: "#6366f1",
            icon: "📱",
          },
          { label: "Android", value: "28%", color: "#10b981", icon: "📲" },
          {
            label: "Peak Hour",
            value: "6–8 PM",
            color: "#f59e0b",
            icon: "⏰",
          },
          {
            label: "Top Day",
            value: "Thursday",
            color: "#ec4899",
            icon: "📅",
          },
        ].map((item) => (
          <div
            key={item.label}
            style={{
              background: "rgba(255,255,255,0.03)",
              border: "1px solid rgba(99,102,241,0.12)",
              borderRadius: "8px",
              padding: "10px",
              textAlign: "center",
            }}
          >
            <div style={{ fontSize: "16px", marginBottom: "4px" }}>
              {item.icon}
            </div>
            <div
              style={{
                color: item.color,
                fontSize: "14px",
                fontWeight: 700,
              }}
            >
              {item.value}
            </div>
            <div
              style={{
                color: "rgba(232,234,246,0.45)",
                fontSize: "10px",
              }}
            >
              {item.label}
            </div>
          </div>
        ))}
      </div>
    </StyledCard>
  </>
);

const NetworkTab = () => (
  <>
    <StyledCard>
      <StyledSectionTitle>Tap Map — 8 Cities</StyledSectionTitle>
      <StyledMapPlaceholder>
        <div className="map-dots">
          {TAP_CITIES.map((city) => (
            <div key={city} className="map-dot" title={city}>
              {city.slice(0, 2)}
            </div>
          ))}
        </div>
        <div className="map-cities">You've tapped in 8 cities</div>
        <div className="map-label">
          Interactive map available with TapID Pro
        </div>
      </StyledMapPlaceholder>
    </StyledCard>

    <StyledCard>
      <StyledSectionTitle>Mutual Connections</StyledSectionTitle>
      <StyledNetworkNode>
        <div className="connections-list">
          {CONNECTIONS.map((conn) => (
            <div key={conn.id} className="connection-item">
              <div
                className="conn-avatar"
                style={
                  {
                    "--c1": conn.c1,
                    "--c2": conn.c2,
                  } as React.CSSProperties
                }
              >
                {conn.initials}
              </div>
              <div className="conn-info">
                <div className="conn-name">{conn.name}</div>
                <div className="conn-mutual">🔗 {conn.mutual}</div>
                <div className="conn-location">📍 {conn.location}</div>
              </div>
              <div className="conn-actions">
                <StyledButton $variant="secondary" $small>
                  View
                </StyledButton>
              </div>
            </div>
          ))}
        </div>
      </StyledNetworkNode>
    </StyledCard>

    <StyledCard>
      <StyledSectionTitle>Profile Verification</StyledSectionTitle>
      {[
        { label: "Email Verified", done: true, icon: "✉️" },
        { label: "LinkedIn Connected", done: true, icon: "💼" },
        { label: "Phone Verified", done: false, icon: "📞" },
        { label: "ID Verified", done: false, icon: "🪪" },
      ].map((item) => (
        <div
          key={item.label}
          style={{
            alignItems: "center",
            borderTop: "1px solid rgba(255,255,255,0.05)",
            display: "flex",
            gap: "10px",
            padding: "10px 0",
          }}
        >
          <span style={{ fontSize: "16px" }}>{item.icon}</span>
          <span style={{ flex: 1, fontSize: "13px" }}>{item.label}</span>
          {item.done ? (
            <span
              style={{
                background: "rgba(16,185,129,0.15)",
                border: "1px solid rgba(16,185,129,0.3)",
                borderRadius: "12px",
                color: "#10b981",
                fontSize: "11px",
                padding: "2px 8px",
              }}
            >
              ✓ Verified
            </span>
          ) : (
            <StyledButton $variant="secondary" $small>
              Verify
            </StyledButton>
          )}
        </div>
      ))}
    </StyledCard>
  </>
);

const SettingsTab = () => {
  const [notifications, setNotifications] = useState({
    tapAlerts: true,
    weeklyDigest: true,
    mutualAlerts: true,
    viewAlerts: false,
  });

  const toggle = (key: keyof typeof notifications) =>
    setNotifications((prev) => ({ ...prev, [key]: !prev[key] }));

  return (
    <>
      <StyledProBanner>
        <div className="pro-title">⚡ TapID Pro</div>
        <div className="pro-price">
          <strong>$4.99</strong>
          /month
        </div>
        <div className="pro-features">
          {[
            "Unlimited profiles",
            "Advanced analytics",
            "Profile scheduling",
            "Custom domain",
            "CRM sync via API",
            "Priority shipping",
            "No 'Powered by' badge",
            "Tap location data",
          ].map((f) => (
            <div key={f} className="pro-feature">
              <span className="check">✓</span> {f}
            </div>
          ))}
        </div>
        <StyledButton>Upgrade to Pro</StyledButton>
      </StyledProBanner>

      <StyledCard>
        <StyledSectionTitle>Notifications</StyledSectionTitle>
        {(
          [
            {
              key: "tapAlerts" as const,
              label: "Tap Alerts",
              desc: "Notify when someone taps your card",
            },
            {
              key: "weeklyDigest" as const,
              label: "Weekly Digest",
              desc: "Summary of your profile activity every week",
            },
            {
              key: "mutualAlerts" as const,
              label: "Mutual Connections",
              desc: "Alert when mutual connections are discovered",
            },
            {
              key: "viewAlerts" as const,
              label: "Profile View Alerts",
              desc: "Notify every time someone views your profile",
            },
          ] as const
        ).map((item) => (
          <div
            key={item.key}
            style={{
              alignItems: "center",
              borderTop: "1px solid rgba(255,255,255,0.05)",
              display: "flex",
              gap: "12px",
              padding: "11px 0",
            }}
          >
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: "13px", fontWeight: 500 }}>
                {item.label}
              </div>
              <div
                style={{
                  color: "rgba(232,234,246,0.45)",
                  fontSize: "11px",
                  marginTop: "2px",
                }}
              >
                {item.desc}
              </div>
            </div>
            <div
              style={{
                background: notifications[item.key]
                  ? "#6366f1"
                  : "rgba(255,255,255,0.1)",
                borderRadius: "10px",
                cursor: "pointer",
                flexShrink: 0,
                height: "20px",
                position: "relative",
                transition: "background 0.2s",
                width: "36px",
              }}
              onClick={() => toggle(item.key)}
            >
              <div
                style={{
                  background: "#fff",
                  borderRadius: "50%",
                  height: "14px",
                  left: notifications[item.key] ? "19px" : "3px",
                  position: "absolute",
                  top: "3px",
                  transition: "left 0.2s",
                  width: "14px",
                }}
              />
            </div>
          </div>
        ))}
      </StyledCard>

      <StyledCard>
        <StyledSectionTitle>Business Plans</StyledSectionTitle>
        <p
          style={{
            color: "rgba(232,234,246,0.5)",
            fontSize: "12px",
            lineHeight: "1.6",
            marginBottom: "14px",
          }}
        >
          Deploy TapID cards across your entire sales team. One admin dashboard,
          auto-updates when employees leave, centrally managed profiles.
        </p>
        <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
          <StyledButton $variant="secondary">View Team Plans</StyledButton>
          <StyledButton $variant="secondary">Contact Sales</StyledButton>
        </div>
      </StyledCard>

      <StyledCard>
        <StyledSectionTitle>Shop</StyledSectionTitle>
        <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
          {[
            {
              name: "NFC Sticker Pack (10×)",
              desc: "Stick on laptops, notebooks, car windows",
              price: "$12.99",
              icon: "🏷️",
            },
            {
              name: "Card of the Season",
              desc: "New limited-edition design every quarter",
              price: "$9.99/qtr",
              icon: "💳",
            },
            {
              name: "Premium Card Replacement",
              desc: "Priority shipping, new design",
              price: "$24.99",
              icon: "✨",
            },
          ].map((item) => (
            <div
              key={item.name}
              style={{
                alignItems: "center",
                background: "rgba(255,255,255,0.03)",
                border: "1px solid rgba(99,102,241,0.12)",
                borderRadius: "8px",
                display: "flex",
                gap: "10px",
                padding: "10px 12px",
              }}
            >
              <span style={{ fontSize: "20px" }}>{item.icon}</span>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: "13px", fontWeight: 600 }}>
                  {item.name}
                </div>
                <div
                  style={{
                    color: "rgba(232,234,246,0.45)",
                    fontSize: "11px",
                  }}
                >
                  {item.desc}
                </div>
              </div>
              <div
                style={{
                  color: "#10b981",
                  fontSize: "12px",
                  fontWeight: 700,
                }}
              >
                {item.price}
              </div>
            </div>
          ))}
        </div>
      </StyledCard>
    </>
  );
};

// ─── Main Component ───────────────────────────────────────────────────────────

const TABS: Array<{
  id: Tab;
  label: string;
  Icon: React.ComponentType;
}> = [
  { id: "dashboard", label: "Dashboard", Icon: ActivityIcon },
  { id: "profiles", label: "Profiles", Icon: ProfilesIcon },
  { id: "analytics", label: "Analytics", Icon: AnalyticsIcon },
  { id: "network", label: "Network", Icon: NetworkIcon },
  { id: "settings", label: "Settings", Icon: SettingsIcon },
];

const TapID: FC<ComponentProcessProps> = () => {
  const [activeTab, setActiveTab] = useState<Tab>("dashboard");
  const [streak] = useState(7);

  const renderContent = () => {
    switch (activeTab) {
      case "dashboard":
        return <DashboardTab />;
      case "profiles":
        return <ProfilesTab />;
      case "analytics":
        return <AnalyticsTab />;
      case "network":
        return <NetworkTab />;
      case "settings":
        return <SettingsTab />;
      default:
        return null;
    }
  };

  return (
    <StyledTapID>
      <StyledHeader>
        <div className="logo">
          <div className="logo-icon">
            <TapIcon />
          </div>
          <span className="logo-text">TapID</span>
          <span className="pro-badge">Pro</span>
        </div>
        <div className="profile-summary">
          <div className="avatar">JD</div>
          <div className="streak">🔥 {streak}</div>
        </div>
      </StyledHeader>

      <StyledTabs>
        {TABS.map(({ id, label, Icon }) => (
          <StyledTab
            key={id}
            $active={activeTab === id}
            onClick={() => setActiveTab(id)}
          >
            <Icon />
            {label}
          </StyledTab>
        ))}
      </StyledTabs>

      <StyledContent>{renderContent()}</StyledContent>
    </StyledTapID>
  );
};

export default memo(TapID);
