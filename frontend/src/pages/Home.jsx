import { useState } from 'react';
import { sendMessage, streamMessage } from '../api/chatApi';
import ChatWindow from '../components/ChatWindow';
import InputBox from '../components/InputBox';
import './Home.css';

function Home() {
  const [messages, setMessages] = useState([]);
  const [sessionId, setSessionId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const addMessage = (role, content, tool = null) => {
    setMessages((current) => [...current, { id: crypto.randomUUID(), role, content, tool }]);
  };

  const updateLastAssistant = (updater) => {
    setMessages((current) =>
      current.map((message, index) => {
        if (index !== current.length - 1 || message.role !== 'assistant') {
          return message;
        }
        return { ...message, content: updater(message.content) };
      })
    );
  };

  const updateLastAssistantTool = (tool) => {
    setMessages((current) =>
      current.map((message, index) => {
        if (index !== current.length - 1 || message.role !== 'assistant') {
          return message;
        }
        return { ...message, tool };
      })
    );
  };

  const handleSend = async ({ message, model, streaming }) => {
    setError('');
    setLoading(true);
    addMessage('user', message);

    try {
      if (!streaming) {
        const payload = await sendMessage({ message, model, sessionId });
        setSessionId((current) => payload.sessionId || current);
        addMessage('assistant', payload.response || '(No response)', payload.tool || null);
      } else {
        addMessage('assistant', '');
        await streamMessage({
          message,
          model,
          sessionId,
          onMetadata: (metadata) => {
            setSessionId((current) => metadata?.sessionId || current);
            updateLastAssistantTool(metadata?.tool || null);
          },
          onToken: (token) => {
            updateLastAssistant((current) => current + token);
          }
        });
      }
    } catch (err) {
      setError(err.message || 'Something went wrong.');
      addMessage('assistant', 'Error calling backend/Ollama. Check backend logs.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="home-page">
      <section className="chat-card">
        <header>
          <h1>LLM Pet Project</h1>
          <p>React + Spring Boot + Ollama</p>
        </header>

        {error ? <div className="error-banner">{error}</div> : null}

        <ChatWindow messages={messages} />

        <InputBox disabled={loading} onSend={handleSend} />
      </section>
    </main>
  );
}

export default Home;
