import {
  Button,
  Column,
  Host,
  LazyColumn,
  ListItem,
  RadioButton,
  Row,
  Text,
  TextButton,
  TextField,
  useMaterialColors,
  useNativeState,
} from '@expo/ui/jetpack-compose';
import { fillMaxSize, fillMaxWidth, paddingAll, weight } from '@expo/ui/jetpack-compose/modifiers';
import { Effect } from 'effect';
import { useState } from 'react';

import { parseProxyAddress, type Proxy } from '@/core/proxy/parse';
import { getActiveProxyId, loadProxies, saveProxies, setActiveProxyId } from '@/core/proxy/store';
import { testProxy } from '@/core/proxy/test';

export default function Proxies() {
  const colors = useMaterialColors();
  const address = useNativeState('');
  const [proxies, setProxies] = useState(loadProxies);
  const [activeId, setActive] = useState(getActiveProxyId);
  const [status, setStatus] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  const add = () => {
    const parsed = parseProxyAddress(address.value);

    if (!parsed) {
      setError('Use host:port, http://host:port or socks5://host:port');

      return;
    }

    setError(null);
    const next: Proxy = { id: Math.random().toString(36).slice(2), name: parsed.host, ...parsed };
    const list = [...proxies, next];
    saveProxies(list);
    setProxies(list);
    address.value = '';
  };

  const remove = (id: string) => {
    const list = proxies.filter((p) => p.id !== id);
    saveProxies(list);
    setProxies(list);

    if (id === activeId) select(null);
  };

  const select = (id: string | null) => {
    setActiveProxyId(id);
    setActive(id);
  };

  const test = async (proxy: Proxy) => {
    setStatus((s) => ({ ...s, [proxy.id]: 'Testing…' }));
    const result = await Effect.runPromise(testProxy(proxy));
    setStatus((s) => ({
      ...s,
      [proxy.id]: result.ok ? `Reachable, ${result.ms} ms` : `Failed: ${result.error}`,
    }));
  };

  return (
    <Host style={{ flex: 1 }}>
      <Column modifiers={[fillMaxSize(), paddingAll(16)]} verticalArrangement={{ spacedBy: 12 }}>
        <TextField value={address} modifiers={[fillMaxWidth()]}>
          <TextField.Label>
            <Text>Proxy address</Text>
          </TextField.Label>
        </TextField>
        {error && <Text color={colors.error}>{error}</Text>}
        <Button onClick={add}>
          <Text>Add proxy</Text>
        </Button>
        <Text color={colors.onSurfaceVariant}>
          {proxies.length === 0
            ? 'No proxies yet. Playback goes direct.'
            : 'Only Twitch playlist requests use the selected proxy.'}
        </Text>
        <LazyColumn modifiers={[weight(1)]}>
          {proxies.map((proxy) => (
            <ListItem key={proxy.id}>
              <ListItem.LeadingContent>
                <RadioButton
                  selected={proxy.id === activeId}
                  onClick={() => select(proxy.id === activeId ? null : proxy.id)}
                />
              </ListItem.LeadingContent>
              <ListItem.HeadlineContent>
                <Text>{`${proxy.host}:${proxy.port}`}</Text>
              </ListItem.HeadlineContent>
              <ListItem.SupportingContent>
                <Text color={colors.onSurfaceVariant}>
                  {status[proxy.id] ?? proxy.type.toUpperCase()}
                </Text>
              </ListItem.SupportingContent>
              <ListItem.TrailingContent>
                <Row>
                  <TextButton onClick={() => test(proxy)}>
                    <Text>Test</Text>
                  </TextButton>
                  <TextButton onClick={() => remove(proxy.id)}>
                    <Text>Delete</Text>
                  </TextButton>
                </Row>
              </ListItem.TrailingContent>
            </ListItem>
          ))}
        </LazyColumn>
      </Column>
    </Host>
  );
}
