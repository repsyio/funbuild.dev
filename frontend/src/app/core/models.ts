export type Role = 'ADMIN' | 'MEMBER';
export type AuthProvider = 'LOCAL' | 'GITHUB' | 'GOOGLE';
export type AssignmentStatus = 'UPCOMING' | 'ACTIVE' | 'EXPIRED';

export interface UserSummary {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: Role;
  authProvider: AuthProvider;
}

export interface Assignment {
  id: number;
  title: string;
  description: string;
  startAt: string;
  endAt: string;
  status: AssignmentStatus;
  createdBy: UserSummary;
  createdAt: string;
}

export interface AssignmentRequest {
  title: string;
  description: string;
  startAt: string;
  endAt: string;
}

export interface TechLabel {
  id: number;
  name: string;
}

export interface Project {
  id: number;
  assignmentId: number;
  assignmentTitle: string;
  submitter: UserSummary;
  title: string;
  description: string;
  showcaseUrl: string;
  gitRepoUrl: string | null;
  techLabels: TechLabel[];
  voteCount: number;
  votedByMe: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectRequest {
  assignmentId: number;
  title: string;
  description: string;
  showcaseUrl: string;
  gitRepoUrl: string | null;
  techLabels: string[];
}

export interface VoteResult {
  voteCount: number;
  votedByMe: boolean;
}

export interface AuthResponse {
  token: string;
  user: UserSummary;
}
