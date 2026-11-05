import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {Rankings} from './LearningTools';
import {api} from './api';

vi.mock('./api',()=>({api:{get:vi.fn()},errorMessage:()=> 'Không tải được dữ liệu'}));
afterEach(()=>{cleanup();vi.clearAllMocks()});

it('học sinh chỉ nhận thứ hạng riêng, không gọi bảng xếp hạng giáo viên',async()=>{
 vi.mocked(api.get).mockImplementation(async(url)=>{
  if(url==='/student/classrooms') return {data:[{id:'class-1',code:'A',name:'Lớp A'}]} as never;
  if(url==='/student/classrooms/class-1/ranking') return {data:{classroomId:'class-1',period:'ALL',cohortSize:20,rank:3,assignedCount:5,completedCount:4,averagePercent:82,completionPercent:80,progressPercent:60,rankingScore:77,formula:'70% điểm + 20% hoàn thành + 10% tiến bộ'}} as never;
  throw new Error(`Unexpected endpoint ${url}`);
 });
 const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
 render(<QueryClientProvider client={queryClient}><Rankings user={{id:'student-1',email:'student@example.invalid',fullName:'Học sinh A',role:'STUDENT',status:'ACTIVE'}}/></QueryClientProvider>);
 await waitFor(()=>expect(screen.getByText('#3')).toBeInTheDocument());
 expect(screen.getByText('Thứ hạng của bạn')).toBeInTheDocument();
 expect(api.get).not.toHaveBeenCalledWith(expect.stringContaining('/admin/'),expect.anything());
});
