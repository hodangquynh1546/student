import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {Profile} from './Profile';
import {api} from './api';
import {useSession} from './session';
import type {User} from './types';

vi.mock('./api',()=>({api:{put:vi.fn(),post:vi.fn()},errorMessage:()=> 'Không lưu được'}));
afterEach(()=>{cleanup();vi.clearAllMocks();useSession.setState({user:null,accessToken:null})});

it('cập nhật hồ sơ của tài khoản đang đăng nhập qua API',async()=>{
 const current:User={id:'student-1',email:'learner@example.test',fullName:'Tên cũ',role:'STUDENT',status:'ACTIVE'};
 useSession.setState({user:current,accessToken:'access'});
 vi.mocked(api.put).mockResolvedValue({data:{...current,fullName:'Tên mới',phone:'0123456789'}});
 render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><Profile/></MemoryRouter></QueryClientProvider>);

 fireEvent.change(screen.getByLabelText('Họ và tên'),{target:{value:'Tên mới'}});
 fireEvent.change(screen.getByLabelText('Số điện thoại'),{target:{value:'0123456789'}});
 fireEvent.click(screen.getByRole('button',{name:'Lưu hồ sơ'}));

 await waitFor(()=>expect(api.put).toHaveBeenCalledWith('/auth/me',{fullName:'Tên mới',phone:'0123456789',dateOfBirth:null}));
 await waitFor(()=>expect(useSession.getState().user?.fullName).toBe('Tên mới'));
});
