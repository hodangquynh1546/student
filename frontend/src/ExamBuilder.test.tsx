import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {ExamBuilder} from './App';
import {api} from './api';
import type {ExamDetail} from './types';

vi.mock('./api',()=>({api:{get:vi.fn(),put:vi.fn(),patch:vi.fn(),delete:vi.fn(),post:vi.fn()},errorMessage:()=> 'Lưu thất bại'}));
afterEach(()=>{cleanup();vi.clearAllMocks()});
const exam:ExamDetail={exam:{id:'exam-1',title:'Kiểm tra phân số',kind:'EXAM',status:'DRAFT',maxAttempts:1,totalPoints:3,questionCount:2,version:1},questions:[
 {id:'q1',sourceQuestionId:'bank-1',position:0,type:'TRUE_FALSE',body:'Câu số một',content:'{}',answerKey:'{"correct":true}',points:1},
 {id:'q2',sourceQuestionId:null,position:1,type:'ESSAY',body:'Câu số hai',content:'{}',answerKey:'{"referenceAnswer":"A"}',points:2}
]};
function view(){
 vi.mocked(api.get).mockImplementation(async url=>{
  if(url==='/admin/exams/exam-1')return {data:exam} as never;
  if(url==='/admin/questions')return {data:{items:[],total:0,page:0,size:100}} as never;
  if(url==='/admin/classrooms')return {data:[]} as never;
  throw new Error(`Unexpected endpoint: ${url}`);
 });
 render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter initialEntries={['/exams/exam-1']}><Routes><Route path="/exams/:id" element={<ExamBuilder/>}/></Routes></MemoryRouter></QueryClientProvider>);
}

it('giáo viên xem trước đáp án và đổi thứ tự câu nháp qua API',async()=>{
 vi.mocked(api.put).mockResolvedValue({data:{...exam,questions:[exam.questions[1],exam.questions[0]]}});
 view();
 expect(await screen.findByText('Câu số một')).toBeInTheDocument();
 fireEvent.click(screen.getByRole('button',{name:'Xem trước'}));
 expect(screen.getByText('Xem trước: Kiểm tra phân số')).toBeInTheDocument();
 fireEvent.click(screen.getByRole('button',{name:'Đóng'}));
 fireEvent.click(screen.getByRole('button',{name:'Đưa câu 1 xuống'}));
 await waitFor(()=>expect(api.put).toHaveBeenCalledWith('/admin/exams/exam-1/questions/order',{questionIds:['q2','q1']}));
});

it('giáo viên tự bấm lưu điểm mới, không gửi cập nhật khi chỉ sửa ô nhập',async()=>{
 vi.mocked(api.patch).mockResolvedValue({data:{...exam,questions:[{...exam.questions[0],points:1.5},exam.questions[1]]}});
 view();
 expect(await screen.findByText('Câu số một')).toBeInTheDocument();
 const points=screen.getAllByRole('spinbutton');
 fireEvent.change(points[0],{target:{value:'1.5'}});
 expect(api.patch).not.toHaveBeenCalled();
 fireEvent.click(screen.getAllByRole('button',{name:'Lưu điểm'})[0]);
 await waitFor(()=>expect(api.patch).toHaveBeenCalledWith('/admin/exams/exam-1/questions/q1/points',{points:1.5}));
});

it('cài đặt đề dùng version hiện tại và gửi đầy đủ cấu hình',async()=>{
 vi.mocked(api.put).mockResolvedValue({data:{...exam.exam,version:2}});
 view();
 expect(await screen.findByText('Câu số một')).toBeInTheDocument();
 fireEvent.click(screen.getByRole('button',{name:'Cài đặt đề'}));
 fireEvent.change(screen.getByLabelText('Tên đề'),{target:{value:'Kiểm tra phân số nâng cao'}});
 fireEvent.click(screen.getByRole('button',{name:'Lưu cài đặt'}));
 await waitFor(()=>expect(api.put).toHaveBeenCalledWith('/admin/exams/exam-1',expect.objectContaining({title:'Kiểm tra phân số nâng cao',kind:'EXAM',maxAttempts:1,shuffleQuestions:false,allowDraft:true}),{params:{version:1}}));
});
