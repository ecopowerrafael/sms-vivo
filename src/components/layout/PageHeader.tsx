import type { ReactNode } from 'react';

export function PageHeader({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) {
  return <header className="page-header"><div><span className="eyebrow">{eyebrow ?? 'SMS VIVO'}</span><h1>{title}</h1>{description && <p className="muted">{description}</p>}</div>{action}</header>;
}

