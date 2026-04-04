import styled from "styled-components";

export const StyledTapID = styled.div`
  background: #0f0f1a;
  color: #e8eaf6;
  display: flex;
  flex-direction: column;
  font-family:
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    Roboto,
    sans-serif;
  height: 100%;
  overflow: hidden;
  width: 100%;
`;

export const StyledHeader = styled.div`
  align-items: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  border-bottom: 1px solid rgba(99, 102, 241, 0.3);
  display: flex;
  flex-shrink: 0;
  justify-content: space-between;
  padding: 12px 16px;

  .logo {
    align-items: center;
    display: flex;
    gap: 8px;

    .logo-icon {
      align-items: center;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      border-radius: 8px;
      display: flex;
      height: 28px;
      justify-content: center;
      width: 28px;

      svg {
        height: 16px;
        width: 16px;
      }
    }

    .logo-text {
      font-size: 16px;
      font-weight: 700;
      letter-spacing: 0.5px;
    }

    .pro-badge {
      background: linear-gradient(135deg, #f59e0b, #ef4444);
      border-radius: 4px;
      color: #fff;
      font-size: 9px;
      font-weight: 700;
      letter-spacing: 0.5px;
      padding: 2px 5px;
      text-transform: uppercase;
    }
  }

  .profile-summary {
    align-items: center;
    display: flex;
    gap: 8px;

    .avatar {
      align-items: center;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      border-radius: 50%;
      color: #fff;
      display: flex;
      font-size: 12px;
      font-weight: 700;
      height: 28px;
      justify-content: center;
      width: 28px;
    }

    .streak {
      align-items: center;
      background: rgba(245, 158, 11, 0.2);
      border: 1px solid rgba(245, 158, 11, 0.4);
      border-radius: 12px;
      color: #f59e0b;
      display: flex;
      font-size: 11px;
      font-weight: 600;
      gap: 3px;
      padding: 2px 8px;
    }
  }
`;

export const StyledTabs = styled.div`
  background: #1a1a2e;
  border-bottom: 1px solid rgba(99, 102, 241, 0.2);
  display: flex;
  flex-shrink: 0;
  overflow-x: auto;

  &::-webkit-scrollbar {
    display: none;
  }
`;

export const StyledTab = styled.button<{ $active: boolean }>`
  align-items: center;
  background: transparent;
  border: none;
  border-bottom: 2px solid
    ${({ $active }) => ($active ? "#6366f1" : "transparent")};
  color: ${({ $active }) =>
    $active ? "#6366f1" : "rgba(232, 234, 246, 0.6)"};
  cursor: pointer;
  display: flex;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: ${({ $active }) => ($active ? "600" : "400")};
  gap: 5px;
  padding: 10px 14px;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover {
    color: #e8eaf6;
  }

  svg {
    height: 13px;
    width: 13px;
  }
`;

export const StyledContent = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 16px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(99, 102, 241, 0.4);
    border-radius: 2px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }
`;

export const StyledCard = styled.div`
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 12px;
  margin-bottom: 12px;
  padding: 14px 16px;
`;

export const StyledSectionTitle = styled.h3`
  color: rgba(232, 234, 246, 0.6);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.8px;
  margin: 0 0 12px;
  text-transform: uppercase;
`;

export const StyledStatGrid = styled.div`
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, 1fr);
  margin-bottom: 14px;

  @media (min-width: 500px) {
    grid-template-columns: repeat(4, 1fr);
  }
`;

export const StyledStatCard = styled.div<{ $color?: string }>`
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 10px;
  padding: 12px;
  text-align: center;

  .stat-value {
    color: ${({ $color }) => $color || "#6366f1"};
    font-size: 22px;
    font-weight: 700;
    line-height: 1;
    margin-bottom: 4px;
  }

  .stat-label {
    color: rgba(232, 234, 246, 0.5);
    font-size: 10px;
    font-weight: 500;
    text-transform: uppercase;
  }

  .stat-delta {
    color: #10b981;
    font-size: 10px;
    margin-top: 2px;
  }
`;

export const StyledFeedItem = styled.div`
  align-items: flex-start;
  display: flex;
  gap: 10px;
  padding: 10px 0;

  & + & {
    border-top: 1px solid rgba(255, 255, 255, 0.05);
  }

  .feed-icon {
    align-items: center;
    border-radius: 50%;
    display: flex;
    flex-shrink: 0;
    height: 32px;
    justify-content: center;
    width: 32px;

    svg {
      height: 14px;
      width: 14px;
    }
  }

  .feed-body {
    flex: 1;
    min-width: 0;

    .feed-title {
      font-size: 13px;
      font-weight: 500;
      line-height: 1.4;
    }

    .feed-meta {
      color: rgba(232, 234, 246, 0.45);
      font-size: 11px;
      margin-top: 2px;
    }
  }

  .feed-time {
    color: rgba(232, 234, 246, 0.35);
    flex-shrink: 0;
    font-size: 10px;
  }
`;

export const StyledProfileCard = styled.div<{ $active: boolean }>`
  border: 2px solid
    ${({ $active }) => ($active ? "#6366f1" : "rgba(99, 102, 241, 0.15)")};
  border-radius: 12px;
  cursor: pointer;
  margin-bottom: 10px;
  overflow: hidden;
  transition: all 0.2s;

  &:hover {
    border-color: rgba(99, 102, 241, 0.5);
  }

  .profile-header {
    align-items: center;
    background: ${({ $active }) =>
      $active
        ? "linear-gradient(135deg, rgba(99,102,241,0.2), rgba(139,92,246,0.2))"
        : "rgba(255,255,255,0.03)"};
    display: flex;
    gap: 12px;
    padding: 14px 16px;

    .profile-mode-icon {
      font-size: 22px;
    }

    .profile-info {
      flex: 1;

      .profile-name {
        font-size: 14px;
        font-weight: 600;
      }

      .profile-desc {
        color: rgba(232, 234, 246, 0.5);
        font-size: 11px;
        margin-top: 2px;
      }
    }

    .profile-status {
      align-items: center;
      display: flex;
      flex-direction: column;
      gap: 4px;

      .active-indicator {
        background: #10b981;
        border-radius: 50%;
        height: 8px;
        width: 8px;
      }

      .tap-count {
        color: rgba(232, 234, 246, 0.4);
        font-size: 10px;
      }
    }
  }

  .profile-links {
    border-top: 1px solid rgba(255, 255, 255, 0.05);
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 10px 16px;
  }
`;

export const StyledLinkChip = styled.span<{ $color?: string }>`
  background: ${({ $color }) => $color || "rgba(99,102,241,0.15)"};
  border: 1px solid ${({ $color }) => $color || "rgba(99,102,241,0.3)"};
  border-radius: 20px;
  color: rgba(232, 234, 246, 0.8);
  font-size: 10px;
  padding: 3px 8px;
`;

export const StyledBarChart = styled.div`
  .bar-row {
    align-items: center;
    display: flex;
    gap: 10px;
    margin-bottom: 8px;

    .bar-label {
      color: rgba(232, 234, 246, 0.7);
      font-size: 12px;
      min-width: 90px;
      text-align: right;
    }

    .bar-track {
      background: rgba(255, 255, 255, 0.06);
      border-radius: 4px;
      flex: 1;
      height: 8px;
      overflow: hidden;

      .bar-fill {
        border-radius: 4px;
        height: 100%;
        transition: width 0.6s ease;
      }
    }

    .bar-value {
      color: rgba(232, 234, 246, 0.6);
      font-size: 11px;
      min-width: 30px;
      text-align: right;
    }
  }
`;

export const StyledNetworkNode = styled.div`
  .connections-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .connection-item {
    align-items: center;
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid rgba(99, 102, 241, 0.12);
    border-radius: 10px;
    display: flex;
    gap: 10px;
    padding: 10px 12px;

    .conn-avatar {
      align-items: center;
      background: linear-gradient(
        135deg,
        var(--c1, #6366f1),
        var(--c2, #8b5cf6)
      );
      border-radius: 50%;
      color: #fff;
      display: flex;
      flex-shrink: 0;
      font-size: 12px;
      font-weight: 700;
      height: 34px;
      justify-content: center;
      width: 34px;
    }

    .conn-info {
      flex: 1;

      .conn-name {
        font-size: 13px;
        font-weight: 600;
      }

      .conn-mutual {
        color: rgba(99, 102, 241, 0.8);
        font-size: 11px;
        margin-top: 2px;
      }

      .conn-location {
        color: rgba(232, 234, 246, 0.4);
        font-size: 10px;
        margin-top: 1px;
      }
    }

    .conn-actions {
      flex-shrink: 0;
    }
  }
`;

export const StyledMapPlaceholder = styled.div`
  align-items: center;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(99, 102, 241, 0.12);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
  min-height: 160px;
  padding: 24px;
  text-align: center;

  .map-dots {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    justify-content: center;
    margin-bottom: 4px;
    max-width: 260px;

    .map-dot {
      align-items: center;
      background: rgba(99, 102, 241, 0.3);
      border: 1px solid rgba(99, 102, 241, 0.5);
      border-radius: 50%;
      display: flex;
      font-size: 8px;
      height: 18px;
      justify-content: center;
      width: 18px;
    }
  }

  .map-label {
    color: rgba(232, 234, 246, 0.5);
    font-size: 12px;
  }

  .map-cities {
    color: #6366f1;
    font-size: 14px;
    font-weight: 700;
  }
`;

export const StyledButton = styled.button<{
  $variant?: "primary" | "secondary" | "danger";
  $small?: boolean;
}>`
  align-items: center;
  background: ${({ $variant }) => {
    switch ($variant) {
      case "secondary":
        return "rgba(99, 102, 241, 0.1)";
      case "danger":
        return "rgba(239, 68, 68, 0.1)";
      default:
        return "linear-gradient(135deg, #6366f1, #8b5cf6)";
    }
  }};
  border: 1px solid
    ${({ $variant }) => {
      switch ($variant) {
        case "secondary":
          return "rgba(99, 102, 241, 0.3)";
        case "danger":
          return "rgba(239, 68, 68, 0.3)";
        default:
          return "transparent";
      }
    }};
  border-radius: 8px;
  color: ${({ $variant }) =>
    $variant === "danger" ? "#ef4444" : "#e8eaf6"};
  cursor: pointer;
  display: inline-flex;
  font-size: ${({ $small }) => ($small ? "11px" : "13px")};
  font-weight: 600;
  gap: 6px;
  padding: ${({ $small }) => ($small ? "5px 10px" : "8px 16px")};
  transition: all 0.2s;

  &:hover {
    filter: brightness(1.15);
  }
`;

export const StyledToggle = styled.label`
  align-items: center;
  cursor: pointer;
  display: flex;
  gap: 10px;

  .toggle-track {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 10px;
    flex-shrink: 0;
    height: 20px;
    position: relative;
    transition: background 0.2s;
    width: 36px;

    input:checked + & {
      background: #6366f1;
    }

    &::after {
      background: #fff;
      border-radius: 50%;
      content: "";
      height: 14px;
      left: 3px;
      position: absolute;
      top: 3px;
      transition: left 0.2s;
      width: 14px;
    }
  }

  input {
    display: none;

    &:checked ~ .toggle-track::after {
      left: 19px;
    }
  }

  .toggle-label {
    font-size: 13px;
  }
`;

export const StyledProBanner = styled.div`
  background: linear-gradient(135deg, #1a1a2e 0%, #0f0f1a 100%);
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: 14px;
  margin-bottom: 14px;
  overflow: hidden;
  padding: 20px;
  position: relative;

  &::before {
    background: radial-gradient(
      ellipse at top right,
      rgba(245, 158, 11, 0.08) 0%,
      transparent 70%
    );
    content: "";
    inset: 0;
    position: absolute;
  }

  .pro-title {
    color: #f59e0b;
    font-size: 16px;
    font-weight: 700;
    margin-bottom: 4px;
  }

  .pro-price {
    color: rgba(232, 234, 246, 0.7);
    font-size: 12px;
    margin-bottom: 14px;

    strong {
      color: #e8eaf6;
      font-size: 22px;
    }
  }

  .pro-features {
    display: grid;
    gap: 6px;
    grid-template-columns: 1fr 1fr;
    margin-bottom: 16px;

    .pro-feature {
      align-items: center;
      color: rgba(232, 234, 246, 0.75);
      display: flex;
      font-size: 11px;
      gap: 5px;

      .check {
        color: #10b981;
        flex-shrink: 0;
      }
    }
  }
`;

export const StyledDigestCard = styled.div`
  background: linear-gradient(
    135deg,
    rgba(99, 102, 241, 0.12),
    rgba(139, 92, 246, 0.08)
  );
  border: 1px solid rgba(99, 102, 241, 0.25);
  border-radius: 12px;
  margin-bottom: 14px;
  padding: 16px;

  .digest-header {
    align-items: center;
    display: flex;
    gap: 8px;
    margin-bottom: 12px;

    .digest-icon {
      color: #6366f1;
      font-size: 18px;
    }

    .digest-title {
      font-size: 14px;
      font-weight: 600;
    }

    .digest-period {
      color: rgba(232, 234, 246, 0.45);
      font-size: 11px;
      margin-left: auto;
    }
  }

  .digest-stats {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(3, 1fr);

    .digest-stat {
      text-align: center;

      .digest-stat-value {
        color: #6366f1;
        font-size: 18px;
        font-weight: 700;
      }

      .digest-stat-label {
        color: rgba(232, 234, 246, 0.5);
        font-size: 10px;
        margin-top: 2px;
      }
    }
  }
`;
