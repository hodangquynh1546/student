import {render,screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {describe,expect,it} from 'vitest';
import {AccountCard} from './AccountCard';

describe('AccountCard',()=>{
 it('opens the profile from the avatar, name and role card',()=>{
  render(<MemoryRouter><AccountCard user={{id:'1',email:'teacher@example.invalid',fullName:'Giáo viên Mẫu',role:'ADMIN_TEACHER',status:'ACTIVE'}}/></MemoryRouter>);
  const link=screen.getByRole('link',{name:'Mở hồ sơ của Giáo viên Mẫu'});
  expect(link.getAttribute('href')).toBe('/profile');
  expect(link.textContent).toContain('Giáo viên Mẫu');
  expect(link.textContent).toContain('Giáo viên');
 });
});
