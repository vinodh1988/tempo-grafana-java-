const feed = document.getElementById('feed');
const scenarioButtons = document.getElementById('scenarioButtons');
const refreshScenariosBtn = document.getElementById('refreshScenarios');
const clearFeedBtn = document.getElementById('clearFeed');
const rpsInput = document.getElementById('rpsInput');
const burstInput = document.getElementById('burstInput');

function appendLog(title, payload) {
  const timestamp = new Date().toISOString();
  const block = [`[${timestamp}] ${title}`, JSON.stringify(payload, null, 2), ''];
  feed.textContent = `${block.join('\n')}${feed.textContent}`;
}

function setBusyState(busy) {
  document.querySelectorAll('button').forEach((button) => {
    button.disabled = busy;
  });
}

async function requestJson(path, options = {}) {
  const response = await fetch(path, {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json'
    }
  });

  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : { empty: true };
  } catch (_error) {
    body = { raw: text };
  }

  if (!response.ok) {
    throw new Error(`HTTP ${response.status} :: ${JSON.stringify(body)}`);
  }

  return body;
}

async function runScenario(scenarioName) {
  setBusyState(true);
  try {
    const result = await requestJson(`/trigger/run?scenario=${encodeURIComponent(scenarioName)}`, {
      method: 'POST'
    });
    appendLog(`Scenario ${scenarioName} executed`, result);
  } catch (error) {
    appendLog(`Scenario ${scenarioName} failed`, { message: error.message });
  } finally {
    setBusyState(false);
  }
}

async function refreshScenarios() {
  setBusyState(true);
  try {
    const result = await requestJson('/trigger/scenarios');
    scenarioButtons.innerHTML = '';

    for (const scenario of result.scenarios || []) {
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = `${scenario.name} :: ${scenario.description}`;
      button.addEventListener('click', () => runScenario(scenario.name));
      scenarioButtons.appendChild(button);
    }

    appendLog('Scenarios loaded', result);
  } catch (error) {
    appendLog('Failed to load scenarios', { message: error.message });
  } finally {
    setBusyState(false);
  }
}

async function runLoadAction(action) {
  setBusyState(true);
  try {
    let path = '/load/status';
    let method = 'GET';

    if (action === 'load-start') {
      method = 'POST';
      path = `/load/start?rps=${encodeURIComponent(rpsInput.value || '2')}`;
    }

    if (action === 'load-stop') {
      method = 'POST';
      path = '/load/stop';
    }

    if (action === 'load-burst') {
      method = 'POST';
      path = `/load/burst?count=${encodeURIComponent(burstInput.value || '25')}`;
    }

    const result = await requestJson(path, { method });
    appendLog(`Load action ${action} completed`, result);
  } catch (error) {
    appendLog(`Load action ${action} failed`, { message: error.message });
  } finally {
    setBusyState(false);
  }
}

refreshScenariosBtn.addEventListener('click', refreshScenarios);
clearFeedBtn.addEventListener('click', () => {
  feed.textContent = '';
});

document.querySelectorAll('button[data-action]').forEach((button) => {
  button.addEventListener('click', () => runLoadAction(button.dataset.action));
});

refreshScenarios();
