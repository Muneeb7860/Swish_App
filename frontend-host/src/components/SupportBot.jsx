import React, { useRef, useEffect } from 'react';
import * as Lucide from 'lucide-react';

export default function SupportBot({
  botOpen,
  setBotOpen,
  botMessages,
  botInputText,
  setBotInputText,
  handleSendBotMessage,
  triggerToast,
  activeRole
}) {
  const messagesEndRef = useRef(null);

  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [botMessages, botOpen]);

  if (!botOpen) {
    return (
      <div id="btn-support-bot-open" className="ai-bot-toggle-btn" onClick={() => setBotOpen(true)}>
        <Lucide.Bot size={24} />
      </div>
    );
  }

  const handleAttachPhoto = () => {
    if (triggerToast) {
      triggerToast('Simulated camera capture: expired_milk.png uploaded.', 'system');
    }
    if (handleSendBotMessage) {
      handleSendBotMessage('https://images.unsplash.com/photo-1550583724-b2692b85b150?auto=format&fit=crop&q=80&w=200');
    }
  };

  const handleVoiceInput = () => {
    setBotInputText('Where is my order?');
    if (triggerToast) {
      triggerToast('Voice recognized: "Where is my order?"', 'system');
    }
  };

  return (
    <div>
      <div id="btn-support-bot-open" className="ai-bot-toggle-btn" onClick={() => setBotOpen(false)}>
        <Lucide.MessageSquareDashed size={24} />
      </div>
      
      <div className="glass-card ai-bot-window">
        <div className="ai-bot-header">
          <div className="ai-bot-header-title">
            <Lucide.Bot size={18} />
            <span>
              {activeRole === 'rider' ? 'Rider Operations Copilot' : activeRole === 'inventory' ? 'Dark Store Picker Assistant' : 'Swiss AI Help Center'}
            </span>
          </div>
          <button id="btn-support-bot-close" className="ai-bot-close-btn" onClick={() => setBotOpen(false)}>
            <Lucide.X size={16} />
          </button>
        </div>
        
        <div className="ai-bot-messages">
          {botMessages.map((msg, idx) => (
            <div key={idx} className={`ai-message ${msg.sender === 'user' ? 'ai-message-user' : 'ai-message-bot'}`}>
              {msg.text}
              {msg.attachmentUrl && (
                <img 
                  src={msg.attachmentUrl} 
                  className="ai-message-attachment" 
                  alt="Simulated vision report upload" 
                />
              )}
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>
        
        <div className="ai-bot-input-area">
          <input 
            id="input-support-bot"
            type="text" 
            className="ai-bot-input" 
            placeholder={activeRole === 'rider' ? "Report breakdown, traffic..." : activeRole === 'inventory' ? "Report damaged, spoiled shelf item..." : "Ask about orders, refunds..."}
            value={botInputText}
            onChange={(e) => setBotInputText(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSendBotMessage()}
          />
          
          <button 
            id="btn-support-bot-attach"
            className="ai-bot-action-btn" 
            title="Attach photo (Simulated)"
            onClick={handleAttachPhoto}
          >
            <Lucide.Image size={16} />
          </button>
          
          <button 
            id="btn-support-bot-voice"
            className="ai-bot-action-btn" 
            title="Send voice message (Simulated)"
            onClick={handleVoiceInput}
          >
            <Lucide.Mic size={16} />
          </button>

          <button id="btn-support-bot-send" className="ai-bot-action-btn" onClick={() => handleSendBotMessage()}>
            <Lucide.Send size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
