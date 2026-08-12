import { cn } from '../../lib/cn.ts';
import type { ExerciseQuestion } from './api.ts';

export interface QuestionListProps {
  readonly questions: ExerciseQuestion[];
  readonly answers: Record<string, string>;
  readonly onSelect: (questionId: string, optionId: string) => void;
  readonly disabled?: boolean;
}

export function QuestionList({ questions, answers, onSelect, disabled }: QuestionListProps) {
  return (
    <ol className="space-y-8">
      {questions.map((question, index) => (
        <li key={question.id}>
          <fieldset disabled={disabled}>
            <legend className="mb-3 text-base font-medium text-ink">
              <span className="mr-2 text-ink-subtle">{index + 1}.</span>
              {question.stem}
            </legend>

            <div className="space-y-2">
              {question.options.map((option) => {
                const selected = answers[question.id] === option.id;
                return (
                  <label
                    key={option.id}
                    className={cn(
                      'flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm transition-colors',
                      selected ? 'border-accent bg-accent-soft' : 'border-line hover:border-accent',
                      disabled && 'cursor-not-allowed opacity-70',
                    )}
                  >
                    <input
                      type="radio"
                      name={question.id}
                      value={option.id}
                      checked={selected}
                      onChange={() => onSelect(question.id, option.id)}
                      className="mt-0.5 accent-[var(--color-accent)]"
                    />
                    <span>
                      <span className="mr-2 font-semibold text-ink-muted">{option.id}</span>
                      {option.text}
                    </span>
                  </label>
                );
              })}
            </div>
          </fieldset>
        </li>
      ))}
    </ol>
  );
}
