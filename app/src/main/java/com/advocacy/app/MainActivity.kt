import React, { useState } from 'react';

export const ThoughtCard = ({ 
  initialText = "", 
  onSaveEntry, 
  onReleaseEntry, 
  onShareWithTeam 
}) => {
  const [isUpdating, setIsUpdating] = useState(false);
  const [thoughtContent, setThoughtContent] = useState(initialText);
  const [holdingBuffer, setHoldingBuffer] = useState(initialText);

  const startChangingWords = () => {
    setHoldingBuffer(thoughtContent);
    setIsUpdating(true);
  };

  const keepTheseWords = () => {
    setThoughtContent(holdingBuffer);
    setIsUpdating(false);
    if (onSaveEntry) {
      onSaveEntry(holdingBuffer);
    }
  };

  const leaveItForNow = () => {
    setHoldingBuffer(thoughtContent);
    setIsUpdating(false);
  };

  const letThisGo = () => {
    setThoughtContent("");
    setHoldingBuffer("");
    setIsUpdating(false);
    if (onReleaseEntry) {
      onReleaseEntry();
    }
  };

  return (
    <div className="thought-container p-4 rounded-xl bg-slate-800 text-slate-100 border border-slate-700 max-w-lg my-2 shadow-sm">
      {isUpdating ? (
        <div className="space-y-3">
          <label className="block text-sm font-medium text-slate-300">
            Thoughts in progress
          </label>
          <textarea
            className="w-full p-3 rounded-lg bg-slate-900 border border-slate-600 text-slate-100 focus:outline-none focus:border-teal-500 transition-colors resize-none"
            rows={4}
            value={holdingBuffer}
            onChange={(e) => setHoldingBuffer(e.target.value)}
            placeholder="Put down whatever is on your mind right now..."
          />
          <div className="flex flex-wrap gap-2 pt-1">
            <button
              type="button"
              onClick={keepTheseWords}
              className="px-4 py-2 bg-teal-700 hover:bg-teal-600 rounded-lg text-sm font-medium transition-colors"
            >
              Keep this
            </button>
            <button
              type="button"
              onClick={leaveItForNow}
              className="px-4 py-2 bg-slate-700 hover:bg-slate-600 rounded-lg text-sm font-medium text-slate-300 transition-colors"
            >
              Leave it for now
            </button>
            <button
              type="button"
              onClick={letThisGo}
              className="px-4 py-2 bg-rose-950/60 hover:bg-rose-900/80 border border-rose-800/50 rounded-lg text-sm font-medium text-rose-300 transition-colors ml-auto"
            >
              Let this go
            </button>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <p className="text-base text-slate-200 whitespace-pre-wrap leading-relaxed">
            {thoughtContent || (
              <span className="italic text-slate-500">
                A quiet space for your thoughts. Nothing written down yet.
              </span>
            )}
          </p>
          <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-slate-700/60">
            <button
              type="button"
              onClick={startChangingWords}
              className="px-3 py-1.5 bg-slate-700/70 hover:bg-slate-700 rounded-md text-xs font-medium text-slate-200 transition-colors"
            >
              Change my words
            </button>
            {onShareWithTeam && thoughtContent && (
              <button
                type="button"
                onClick={() => onShareWithTeam(thoughtContent)}
                className="px-3 py-1.5 bg-sky-900/60 hover:bg-sky-800/80 border border-sky-700/50 rounded-md text-xs font-medium text-sky-200 transition-colors"
              >
                Share when ready
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
