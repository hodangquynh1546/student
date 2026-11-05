import {create} from 'zustand';
import type {Session,User} from './types';
type State={accessToken:string|null;user:User|null;setSession:(s:Session)=>void;updateUser:(user:User)=>void;clear:()=>void};
export const useSession=create<State>(set=>({accessToken:null,user:null,setSession:s=>set({accessToken:s.accessToken,user:s.user}),updateUser:user=>set({user}),clear:()=>set({accessToken:null,user:null})}));
