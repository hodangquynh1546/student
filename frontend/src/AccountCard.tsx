import {ChevronRight} from 'lucide-react';
import {Link} from 'react-router-dom';
import type {User} from './types';
import './account-card.css';

const roleNames:Record<User['role'],string>={
 ADMIN_TEACHER:'Giáo viên',STUDENT:'Học sinh',PARENT:'Phụ huynh',
};

export function AccountIdentity({user,large=false}:{user:User;large?:boolean}){
 const initial=user.fullName.trim().slice(0,1).toLocaleUpperCase('vi')||'?';
 return <span className={`account-identity${large?' account-identity-large':''}`}>
  <span className="account-avatar" aria-hidden="true">{user.avatarUrl?<img src={user.avatarUrl} alt=""/>:initial}</span>
  <span className="account-copy"><strong>{user.fullName}</strong><small>{roleNames[user.role]}</small></span>
 </span>;
}

export function AccountCard({user,onClick}:{user:User;onClick?:()=>void}){
 return <Link to="/profile" className="account-card" onClick={onClick} aria-label={`Mở hồ sơ của ${user.fullName}`}>
  <AccountIdentity user={user}/><ChevronRight size={17} className="account-card-arrow" aria-hidden="true"/>
 </Link>;
}
