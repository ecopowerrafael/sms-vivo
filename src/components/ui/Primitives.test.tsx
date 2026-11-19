import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { describe, expect, it } from 'vitest';
import { Notice } from './Primitives';

describe('Notice', () => {
  it('renders text passed through the app notice object', async () => {
    const host = document.createElement('div');
    const root = createRoot(host);
    await act(async () => { root.render(<Notice text="Campanha criada." tone="success" />); });
    expect(host.textContent).toContain('Campanha criada.');
    expect(host.querySelector('[role="status"]')).not.toBeNull();
    root.unmount();
  });
});
