import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '../components/ui/dialog';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs';
import { Switch } from '../components/ui/switch';
import { Popover, PopoverTrigger, PopoverContent } from '../components/ui/popover';
import { Tooltip, TooltipTrigger, TooltipContent, TooltipProvider } from '../components/ui/tooltip';
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from '../components/ui/dropdown-menu';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '../components/ui/select';

describe('Kit ui (shadcn + Radix)', () => {
  it('dialog s’ouvre et affiche son contenu', () => {
    render(
      <Dialog>
        <DialogTrigger>Ouvrir</DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Titre</DialogTitle>
            <DialogDescription>Description</DialogDescription>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    );
    fireEvent.click(screen.getByText('Ouvrir'));
    expect(screen.getByText('Titre')).toBeInTheDocument();
  });

  it('tabs affiche le panneau actif', () => {
    render(
      <Tabs defaultValue="a">
        <TabsList>
          <TabsTrigger value="a">Onglet A</TabsTrigger>
          <TabsTrigger value="b">Onglet B</TabsTrigger>
        </TabsList>
        <TabsContent value="a">Panneau A</TabsContent>
        <TabsContent value="b">Panneau B</TabsContent>
      </Tabs>
    );
    expect(screen.getByText('Panneau A')).toBeInTheDocument();
    expect(screen.queryByText('Panneau B')).not.toBeInTheDocument();
  });

  it('switch expose le rôle switch', () => {
    const { container } = render(<Switch aria-label="Activer" />);
    expect(container.querySelector('[role="switch"]')).toBeTruthy();
  });

  it('popover s’ouvre au clic', () => {
    render(
      <Popover>
        <PopoverTrigger>Détails</PopoverTrigger>
        <PopoverContent>Contenu du popover</PopoverContent>
      </Popover>
    );
    fireEvent.click(screen.getByText('Détails'));
    expect(screen.getByText('Contenu du popover')).toBeInTheDocument();
  });

  it('tooltip s’affiche au survol', () => {
    render(
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger>Info</TooltipTrigger>
          <TooltipContent>Message d’aide</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
    fireEvent.focus(screen.getByText('Info'));
    fireEvent.mouseEnter(screen.getByText('Info'));
    expect(screen.getByText('Message d’aide')).toBeInTheDocument();
  });

  it('menu déroulant liste ses items', () => {
    render(
      <DropdownMenu defaultOpen>
        <DropdownMenuTrigger>Profil</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>Mon espace</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    );
    expect(screen.getByText('Mon espace')).toBeInTheDocument();
    expect(screen.getByText('Profil')).toBeInTheDocument();
  });

  it('select rend son déclencheur avec la valeur', () => {
    render(
      <Select defaultValue="x">
        <SelectTrigger aria-label="Statut">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="x">En cours</SelectItem>
        </SelectContent>
      </Select>
    );
    expect(screen.getByLabelText('Statut')).toBeInTheDocument();
  });
});
