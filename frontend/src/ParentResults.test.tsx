import {afterEach,expect,it,vi} from 'vitest';
import {render,screen,cleanup} from '@testing-library/react';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {ParentResults} from './ParentResults';
import {api} from './api';

vi.mock('./api',()=>({api:{get:vi.fn()},errorMessage:()=> 'Không tải được dữ liệu'}));
afterEach(()=>{cleanup();vi.clearAllMocks()});

function view(){
 const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
 return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/children/child-1/results']}>
  <Routes><Route path="/children/:studentId/results" element={<ParentResults/>}/></Routes>
 </MemoryRouter></QueryClientProvider>);
}

it('shows only published results returned by the parent API',async()=>{
 vi.mocked(api.get).mockResolvedValue({data:{items:[{attemptId:'attempt-1',examId:'exam-1',examTitle:'Bài toán 1',kind:'EXAM',score:8,totalPoints:10,submittedAt:'2026-01-01T00:00:00Z',publishedAt:'2026-01-02T00:00:00Z'}],total:1,page:0,size:20}});
 view();
 expect(await screen.findByText('Bài toán 1')).toBeInTheDocument();
 expect(screen.getByText('8 / 10 điểm')).toBeInTheDocument();
 expect(screen.getByRole('link',{name:'Chi tiết'})).toHaveAttribute('href','/children/child-1/results/attempt-1');
 expect(api.get).toHaveBeenCalledWith('/parent/children/child-1/results');
});

it('shows an empty state when no result has been published',async()=>{
 vi.mocked(api.get).mockResolvedValue({data:{items:[],total:0,page:0,size:20}});
 view();
 expect(await screen.findByText('Chưa có kết quả được công bố.')).toBeInTheDocument();
});
