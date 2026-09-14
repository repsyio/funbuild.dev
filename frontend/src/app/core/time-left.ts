/** Renders the time remaining until an ISO instant, e.g. "3 days left" or "Ended". */
export function timeLeft(iso: string): string {
  const diffMs = new Date(iso).getTime() - Date.now();
  if (diffMs <= 0) {
    return 'Ended';
  }
  const days = Math.floor(diffMs / 86_400_000);
  if (days >= 1) {
    return `${days} day${days === 1 ? '' : 's'} left`;
  }
  const hours = Math.floor(diffMs / 3_600_000);
  if (hours >= 1) {
    return `${hours} hour${hours === 1 ? '' : 's'} left`;
  }
  const minutes = Math.max(1, Math.floor(diffMs / 60_000));
  return `${minutes} minute${minutes === 1 ? '' : 's'} left`;
}
