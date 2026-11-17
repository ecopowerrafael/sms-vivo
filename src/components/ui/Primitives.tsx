import type { ReactNode } from 'react';

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`card ${className}`.trim()}>{children}</section>;
}

export function Notice({ children, tone = 'info' }: { children?: ReactNode; tone?: 'info' | 'success' | 'danger' }) {
  if (!children) return null;
  return <p className={`notice notice-${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>{children}</p>;
}

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return <div className="empty-state"><span className="empty-icon" aria-hidden="true">○</span><strong>{title}</strong>{description && <span>{description}</span>}</div>;
}

export function StatusBadge({ state }: { state: string }) {
  return <span className={`state state-${state.toLowerCase()}`}>{state}</span>;
}

export function StatCard({ label, value, tone = 'default' }: { label: string; value: string | number; tone?: 'default' | 'success' | 'warning' | 'danger' }) {
  return <div className={`stat-card stat-${tone}`}><span>{label}</span><strong>{value}</strong></div>;
}

export function SectionHeading({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return <div className="section-heading"><div><h2>{title}</h2>{description && <p className="muted">{description}</p>}</div>{action}</div>;
}

